package com.ff.fojsandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.model.ExecuteMessage;
import com.ff.fojsandbox.model.JudgeInfo;
import com.ff.fojsandbox.utils.ProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.util.StopWatch;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxImpl implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 2L; // 2秒执行超时
    private static final List<String> blackList = Arrays.asList("Files", "exec");
    private static final WordTree WORD_TREE;
    private static final String IMAGE = "eclipse-temurin:8-jdk-alpine";

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
        // 创建容器
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(64 * 1000 * 1000L); // 限制内存为64MB
        hostConfig.withMemorySwap(0L); // 禁止使用交换空间
        hostConfig.withCpuCount(1L); // 限制CPU数量
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app"))); // 将用户代码目录挂载到容器的/app目录
        hostConfig.withReadonlyRootfs(true);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .withCmd("tail", "-f", "/dev/null") // 必须加这行！
                .exec();
        String containerId = createContainerResponse.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 执行代码
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            String[] inputArgs = input.split(" ");
            String[] runCmd = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgs);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(runCmd)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            String cmdId = execCreateCmdResponse.getId();
            ExecuteMessage executeMessage = new ExecuteMessage();
            // 统计程序占用内存
            final Long[] memory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            statsCmd.exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("当前内存使用: " + statistics.getMemoryStats().getUsage());
                    memory[0] = Math.max(memory[0], statistics.getMemoryStats().getUsage());
                }
            });
            // 执行命令
            final String[] message = new String[1];
            final String[] errorMessage = new String[1];
            // 回调函数 用来获取执行结果
            ResultCallback.Adapter<Frame> execStartCmdResultCallback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    String mes = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (streamType == StreamType.STDERR) {
                        errorMessage[0] = mes;
                    }else{
                        message[0] = mes;
                    }
                }
            };
            StopWatch stopWatch = new StopWatch();
            long time;
            try {
                stopWatch.start();
                dockerClient.execStartCmd(cmdId)
                        .exec(execStartCmdResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                stopWatch.stop();
                time = stopWatch.getTotalTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(memory[0]);
            executeMessageList.add(executeMessage);
        }
        // 清理容器
        dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
        System.out.println("容器清理成功");
        // 整理返回结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        long maxMemory = 0L;
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
        JavaDockerCodeSandboxImpl javaNativeCodeSandbox = new JavaDockerCodeSandboxImpl();
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
