package com.ff.fojsandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.model.ExecuteMessage;
import com.ff.fojsandbox.model.JudgeInfo;
import com.ff.fojsandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandboxImpl implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final int TIME_OUT = 2000; // 2秒执行超时
    private static final List<String> blackList = Arrays.asList("Files", "exec");
    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        // TODO 多语言支持
        String language = executeCodeRequest.getLanguage();

        // 检查是否包含黑名单词汇
        FoundWord word = WORD_TREE.matchWord(code);
        if (word != null) {
            ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
            executeCodeResponse.setStatus(4); // 代码异常
            executeCodeResponse.setMessage("代码中包含敏感词汇: " + word);
            return executeCodeResponse;
        }

        // 写入用户代码到临时文件
        String userDir = System.getProperty("user.dir");
        String tmpCodePath = userDir + File.separator + GLOBAL_CODE_PATH;
        String userCodeParentPath = tmpCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 编译代码
        String compileCommand = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process process = Runtime.getRuntime().exec(compileCommand);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process);
            // 编译错误直接返回
            if(StrUtil.isNotBlank(executeMessage.getErrorMessage())){
                ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
                executeCodeResponse.setStatus(0); // 编译错误
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                boolean del = FileUtil.del(userCodeParentPath);
                System.out.println("临时代码文件删除" + (del ? "成功" : "失败"));
                return executeCodeResponse;
            }
        } catch (Exception e) {
            return getErrorExecuteCodeResponse(e, userCodeParentPath);
        }
        // 运行代码
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String execCommand = String.format("java " +
                    "-Dfile.encoding=UTF-8 " +
                    "-Xmx256m -Xms64m " +
                    "-cp %s;%s " +
                    "-Djava.security.manager=MySecurityManager " +
                    "Main %s", userCodeParentPath, "D:\\Code\\OJ\\foj-sandbox\\src\\main\\resources\\security", inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(execCommand);
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT); // 2秒后强制销毁进程
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    process.destroy();
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorExecuteCodeResponse(e, userCodeParentPath);
            }
        }
        // 整理返回结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String result = executeMessage.getMessage();
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setStatus(3); // 执行错误
                executeCodeResponse.setMessage(errorMessage);
                break;
            }
            outputList.add(executeMessage.getMessage());
            if (executeMessage.getTime() != null) {
                maxTime = Math.max(maxTime, executeMessage.getTime());
            }
        }
        if (outputList.size() == inputList.size()) {
            executeCodeResponse.setStatus(1); // 执行成功
            executeCodeResponse.setMessage("执行成功");
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // judgeInfo.setMemory();
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        // 清理文件
        boolean del = FileUtil.del(userCodeParentPath);
        System.out.println("临时代码文件删除" + (del ? "成功" : "失败"));
        return executeCodeResponse;
    }

    /**
     * 生成异常响应
     * @param e 异常信息
     * @return 异常响应
     */
    private ExecuteCodeResponse getErrorExecuteCodeResponse(Throwable e, String userCodeParentPath) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setStatus(2); // 代码沙箱异常
        executeCodeResponse.setMessage(e.getMessage());
        // 清理文件
        boolean del = FileUtil.del(userCodeParentPath);
        System.out.println("临时代码文件删除" + (del ? "成功" : "失败"));
        return executeCodeResponse;
    }

    public static void main(String[] args) {
        JavaNativeCodeSandboxImpl javaNativeCodeSandbox = new JavaNativeCodeSandboxImpl();
        String code = ResourceUtil.readStr("Error/Main.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .inputList(Arrays.asList("1 2", "1 3"))
                .code(code)
                .language("java")
                .build();
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }
}
