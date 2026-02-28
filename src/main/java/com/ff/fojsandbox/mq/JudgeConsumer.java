package com.ff.fojsandbox.mq;

import cn.hutool.json.JSONUtil;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.CodeSandbox;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "sandbox.rabbitmq", name = "enabled", havingValue = "true")
public class JudgeConsumer {

    @Resource
    private Map<String, CodeSandbox> codeSandboxMap;

    @Resource
    private ResultHandler resultHandler;

    // C++
    @RabbitListener(
            queues = {"judge_queue_cpp"},
            ackMode = "MANUAL",
            concurrency = "${sandbox.judge.concurrences.cpp:1}"
    )
    public void receiveCpp(String message, Channel channel, Message delivery) throws IOException {
        doExecute(message, channel, delivery);
    }

    // python
    @RabbitListener(
            queues = "judge_queue_python",
            ackMode = "MANUAL",
            concurrency = "${sandbox.judge.concurrences.python:1}"
    )
    public void receivePython(String message, Channel channel, Message delivery) throws IOException {
        doExecute(message, channel, delivery);
    }

    // java
    @RabbitListener(
            queues = "judge_queue_java",
            ackMode = "MANUAL",
            concurrency = "${sandbox.judge.concurrences.java:1}"
    )
    public void receiveJava(String message, Channel channel, Message delivery) throws IOException {
        doExecute(message, channel, delivery);
    }

    /**
     * 通用执行逻辑封装
     */
    private void doExecute(String message, Channel channel, Message delivery) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        ExecuteCodeRequest request = JSONUtil.toBean(message, ExecuteCodeRequest.class);
        String language = request.getLanguage();
        String submissionId = request.getSubmissionId();
        try {
            // 执行代码
            ExecuteCodeResponse response = codeSandboxMap.get(language).executeCode(request);
            // 处理结果 根据配置选择不同的结果处理方式
            resultHandler.handleResult(submissionId, response);
            channel.basicAck(deliveryTag, false); // 手动 ACK
        } catch (Exception e) {
            log.error("判题异常", e);
            // 失败不重回队列，防止死循环阻塞
            channel.basicNack(deliveryTag, false, false);
        }
    }
}