package com.ff.fojsandbox.mq;

import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.model.JudgeResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "sandbox.rabbitmq", name = "result-type", havingValue = "mq")
public class MQResultHandler implements ResultHandler {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void handleResult(String submissionId, ExecuteCodeResponse response) {
        log.info("使用RabbitMQ处理结果: submissionId={}, response={}", submissionId, response);
        JudgeResultEvent judgeResultEvent = new JudgeResultEvent(submissionId, response);
        rabbitTemplate.convertAndSend("judge_result_exchange", "judge_result_routing_key", judgeResultEvent);
    }
}
