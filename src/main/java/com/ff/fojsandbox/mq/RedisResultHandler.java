package com.ff.fojsandbox.mq;

import cn.hutool.json.JSONUtil;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "sandbox.rabbitmq", name = "result-type", havingValue = "redis")
public class RedisResultHandler implements ResultHandler {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public void handleResult(String submissionId, ExecuteCodeResponse response) {
        log.info("使用Redis处理结果: submissionId={}, response={}", submissionId, response);
        String key = "judge:result:" + submissionId;
        String jsonStr = JSONUtil.toJsonStr(response);
        // 将结果存入Redis，设置过期时间为1小时
        stringRedisTemplate.opsForValue().set(key, jsonStr, 1, TimeUnit.HOURS);
        // 通过Redis发布订阅机制通知结果已准备好
        stringRedisTemplate.convertAndSend("judge_result_channel", submissionId);
    }
}
