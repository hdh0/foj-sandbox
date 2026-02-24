package com.ff.fojsandbox;

import cn.hutool.core.io.FileUtil;
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

public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

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
        // 静态代码检查
        if(!checkCode(code)){
            return getErrorExecuteCodeResponse(new RuntimeException("代码中包含敏感词汇"));
        }
        // 写入代码到文件
        File userCodeFile = writeCodeToFile(code);
        // 编译代码
        ExecuteMessage executeMessage = compileCode(userCodeFile);
        // 编译错误直接返回
        if(executeMessage.getExitCode() != 0 || StrUtil.isNotBlank(executeMessage.getErrorMessage())){
            return getErrorExecuteCodeResponse(new RuntimeException(executeMessage + "\n" + executeMessage.getErrorMessage()));
        }
        // 运行代码
        List<ExecuteMessage> executeMessageList = runCode(inputList, userCodeFile);
        // 整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(inputList, executeMessageList);
        // 清理文件
        cleanFile(userCodeFile);
        return executeCodeResponse;
    }

    /**
     * 静态代码检查
     */
    public boolean checkCode(String code) {
        FoundWord word = WORD_TREE.matchWord(code);
        return word == null;
    }

    /**
     * 写入代码到文件
     */
    public File writeCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String tmpCodePath = userDir + File.separator + GLOBAL_CODE_PATH;
        String userCodeParentPath = tmpCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 编译代码
     */
    public ExecuteMessage compileCode(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        String compileCommand = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process process = Runtime.getRuntime().exec(compileCommand, null, new File(userCodeParentPath));
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process);
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 运行代码
     */
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String execCommand = String.format("java " +
                    "-Dfile.encoding=UTF-8 " +
                    "-Xmx256m -Xms64m -cp %s Main %s", userCodeParentPath, inputArgs);
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
                throw new RuntimeException(e);
            }
        }
        return executeMessageList;
    }

    /**
     * 整理输出结果
     */
    public ExecuteCodeResponse getOutputResponse(List<String> inputList, List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxMemory = 0L;
        long maxTime = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
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
            if (executeMessage.getMemory() != null) {
                maxMemory = Math.max(maxMemory, executeMessage.getMemory());
            }
        }
        if (outputList.size() == inputList.size()) {
            executeCodeResponse.setStatus(1); // 执行成功
            executeCodeResponse.setMessage("执行成功");
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 清理文件
     */
    public void cleanFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        boolean del = FileUtil.del(userCodeParentPath);
        System.out.println("临时代码文件删除" + (del ? "成功" : "失败"));
    }

    /**
     * 生成异常响应
     * @param e 异常信息
     * @return 异常响应
     */
    private ExecuteCodeResponse getErrorExecuteCodeResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setStatus(2); // 代码沙箱异常
        executeCodeResponse.setMessage(e.getMessage());
        return executeCodeResponse;
    }

}
