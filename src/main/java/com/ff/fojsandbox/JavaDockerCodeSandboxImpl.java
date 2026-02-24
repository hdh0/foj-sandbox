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
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandboxImpl extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 2L; // 2秒执行超时
    private static final String IMAGE = "eclipse-temurin:8-jdk-alpine";
    private static final DockerClient dockerClient;

    static {
        // 初始化 Docker 客户端
        dockerClient = DockerClientBuilder.getInstance().build();
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    @Override
    public List<ExecuteMessage> runCode(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 创建容器
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
            final StringBuilder messageBuilder = new StringBuilder();
            final StringBuilder errorBuilder = new StringBuilder();
            // 回调函数 用来获取执行结果
            ResultCallback.Adapter<Frame> execStartCmdResultCallback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    String mes = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (streamType == StreamType.STDERR) {
                        errorBuilder.append(mes);
                    } else {
                        messageBuilder.append(mes);
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
            executeMessage.setMessage(messageBuilder.toString());
            executeMessage.setErrorMessage(errorBuilder.toString());
            executeMessage.setTime(time);
            executeMessage.setMemory(memory[0]);
            executeMessageList.add(executeMessage);
        }
        // 清理容器
        dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
        System.out.println("容器清理成功");
        return executeMessageList;
    }
}
