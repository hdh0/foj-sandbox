package com.ff.fojsandbox.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.model.ExecuteMessage;
import com.ff.fojsandbox.model.JudgeInfo;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class DockerCodeSandboxTemplate implements CodeSandbox {

    private static final String GLOBAL_CODE_PATH = "tmpCode";
    private static final long TIME_OUT = 2; // 2秒超时
    private static final List<String> blackList = Arrays.asList("Files", "exec");
    private static final WordTree WORD_TREE;

    @Resource
    protected DockerClient dockerClient;

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

        // 创建并启动容器
        String containerId = createAndStartContainer();

        // 复制代码文件到容器中
        copyCodeToContainer(containerId, userCodeFile);

        // 编译代码
        if(needCompile()){
            ExecuteMessage executeMessage = compileCode(containerId);
            // 编译错误直接返回
            if(StrUtil.isNotBlank(executeMessage.getErrorMessage())){
                return getErrorExecuteCodeResponse(new RuntimeException(executeMessage.getErrorMessage()), containerId, userCodeFile);
            }
        }
        // 运行代码
        List<ExecuteMessage> executeMessageList = runCode(containerId, inputList);

        // 整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(inputList, executeMessageList);

        // 清理资源
        cleanResource(containerId, userCodeFile);
        return executeCodeResponse;
    }

    // 子类必须实现的抽象方法
    // 保存文件名
    protected abstract String getFileName();
    // 是否编译
    protected abstract boolean needCompile();
    // 使用的镜像
    protected abstract String getImage();
    // 编译命令
    protected abstract String[] getCompileCommand();
    // 运行命令
    protected abstract String[] getRunCommand();

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
        String fileName = getFileName();
        String tmpCodePath = userDir + File.separator + GLOBAL_CODE_PATH;
        String userCodeParentPath = tmpCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + fileName;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 创建并启动容器
     */
    public String createAndStartContainer() {
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(getImage());
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(64 * 1000 * 1000L); // 限制内存为64MB
        hostConfig.withMemorySwap(0L); // 禁止使用交换空间
        hostConfig.withCpuCount(1L); // 限制CPU数量
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withWorkingDir("/app") // 设置工作目录
                .withCmd("sh", "-c", "mkdir -p /app && tail -f /dev/null") // 启动时强制创建目录
                .exec();
        String containerId = createContainerResponse.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    /**
     * 复制代码文件到容器中
     */
    public void copyCodeToContainer(String containerId, File userCodeFile) {
        dockerClient.copyArchiveToContainerCmd(containerId)
                .withHostResource(userCodeFile.getAbsolutePath())
                .withRemotePath("/app")
                .exec();
    }

    /**
     * 编译代码
     */
    public ExecuteMessage compileCode(String containerId) {
        String[] compileCommand = getCompileCommand();
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(compileCommand)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        String cmdId = execCreateCmdResponse.getId();
        ExecuteMessage executeMessage = new ExecuteMessage();
        final StringBuilder messageBuilder = new StringBuilder();
        final StringBuilder errorBuilder = new StringBuilder();
        try {
            dockerClient.execStartCmd(cmdId)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getStreamType() == StreamType.STDERR) {
                                errorBuilder.append(new String(frame.getPayload()));
                            } else {
                                messageBuilder.append(new String(frame.getPayload()));
                            }
                        }
                    })
                    .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executeMessage.setMessage(messageBuilder.toString());
        executeMessage.setErrorMessage(errorBuilder.toString());
        return executeMessage;
    }

    /**
     * 运行代码
     */
    public List<ExecuteMessage> runCode(String containerId, List<String> inputList) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            String[] runCmd = getRunCommand();
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
            final StringBuilder messageBuilder = new StringBuilder();
            final StringBuilder errorBuilder = new StringBuilder();
            // 将输入写入到命令的标准输入中
            String formattedInput = input.endsWith("\n") ? input : input + "\n";
            InputStream stdinStream = new ByteArrayInputStream(formattedInput.getBytes(StandardCharsets.UTF_8));
            StopWatch stopWatch = new StopWatch();
            long time;
            try {
                stopWatch.start();
                dockerClient.execStartCmd(cmdId)
                        .withStdIn(stdinStream)
                        .exec(new ResultCallback.Adapter<Frame>() {
                            @Override
                            public void onNext(Frame frame) {
                                if (frame.getStreamType() == StreamType.STDERR) {
                                    errorBuilder.append(new String(frame.getPayload()));
                                } else {
                                    messageBuilder.append(new String(frame.getPayload()));
                                }
                            }
                        })
                        .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                stopWatch.stop();
                time = stopWatch.getTotalTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(messageBuilder.toString());
            executeMessage.setErrorMessage(errorBuilder.toString());
            executeMessage.setTime(time);
            executeMessage.setMemory(memory[0]);
            executeMessageList.add(executeMessage);
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
     * 清理资源
     */
    public void cleanResource(String containerId, File userCodeFile) {
        // 清理容器
        dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
        System.out.println("容器清理成功");
        // 清理临时代码文件
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
        log.trace("代码沙箱异常:", e);
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setStatus(2); // 代码沙箱异常
        executeCodeResponse.setMessage(e.getMessage());
        return executeCodeResponse;
    }

    private ExecuteCodeResponse getErrorExecuteCodeResponse(Throwable e, String containerId, File userCodeFile) {
        ExecuteCodeResponse executeCodeResponse = getErrorExecuteCodeResponse(e);
        cleanResource(containerId, userCodeFile);
        return executeCodeResponse;
    }
}
