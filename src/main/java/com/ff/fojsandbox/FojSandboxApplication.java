package com.ff.fojsandbox;

import com.ff.fojsandbox.config.SandboxConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

@SpringBootApplication
public class FojSandboxApplication {

    @Resource
    private SandboxConfig sandboxConfig;

    public static void main(String[] args) {
        SpringApplication.run(FojSandboxApplication.class, args);
    }
}
