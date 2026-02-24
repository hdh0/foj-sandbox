package com.ff.fojsandbox.controller;

import com.ff.fojsandbox.JavaDockerCodeSandboxImpl;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("/")
public class MainController {

    @Resource
    JavaDockerCodeSandboxImpl javaDockerCodeSandboxImpl;

    @GetMapping("/ping")
    public String checkHealth() {
        return "pong";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandboxImpl.executeCode(executeCodeRequest);
    }
}
