package com.ff.fojsandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "sandbox")
public class SandboxConfig {

    private RabbitmqConfig rabbitmq;
    private JudgeConfig judge;

    @Data
    public static class JudgeConfig {
        private Map<String, Integer> poolSizes; // 不同语言的线程池大小配置
        private Map<String, Integer> concurrences; // 不同语言的预取数量配置
    }

    @Data
    public static class RabbitmqConfig {
        private boolean enabled;
        private String resultType;
    }
}
