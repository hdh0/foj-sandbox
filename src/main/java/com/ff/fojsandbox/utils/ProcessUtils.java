package com.ff.fojsandbox.utils;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.ff.fojsandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ProcessUtils {
    /**
     * 运行进程并获取执行信息
     * 基于 args 传递参数
     */
    public static ExecuteMessage runProcessAndGetMessage(Process process) throws InterruptedException, IOException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        ExecuteMessage executeMessage = new ExecuteMessage();
        int exitCode = process.waitFor(); // 等待进程执行完成并获取退出码
        executeMessage.setExitCode(exitCode);
        if (exitCode == 0) {
            // 分批获取进程的正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder outputStringBuilder = new StringBuilder();
            // 逐行读取
            String outputLine;
            while ((outputLine = bufferedReader.readLine()) != null) {
                outputStringBuilder.append(outputLine).append("\n");
            }
            executeMessage.setMessage(StrUtil.trim(outputStringBuilder.toString()));
            bufferedReader.close();
        } else {
            // 正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder outputStringBuilder = new StringBuilder();
            String outputLine;
            while ((outputLine = bufferedReader.readLine()) != null) {
                outputStringBuilder.append(outputLine);
            }
            String result = StrUtil.trim(outputStringBuilder.toString());
            executeMessage.setMessage(result);
            // 异常输出
            BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutputStringBuilder = new StringBuilder();
            String errorOutputLine;
            while ((errorOutputLine = errorBufferedReader.readLine()) != null) {
                errorOutputStringBuilder.append(errorOutputLine).append("\n");
            }
            String errorResult = StrUtil.trim(errorOutputStringBuilder.toString());
            executeMessage.setErrorMessage(errorResult);
            bufferedReader.close();
            errorBufferedReader.close();
        }
        stopWatch.stop();
        executeMessage.setTime(stopWatch.getTotalTimeMillis());
        return executeMessage;
    }
}
