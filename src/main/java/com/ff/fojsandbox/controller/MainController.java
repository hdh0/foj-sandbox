package com.ff.fojsandbox.controller;

import com.ff.fojsandbox.config.SandboxConfig;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.CodeSandbox;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController("/")
public class MainController {

    @Resource
    private Map<String, CodeSandbox> codeSandboxMap;

    @Resource
    private SandboxConfig sandboxConfig;

    @GetMapping("/ping")
    public String checkHealth() {
        System.out.println("SandboxConfig: " + sandboxConfig);
        return "pong";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        String language = executeCodeRequest.getLanguage();
        return codeSandboxMap.get(language).executeCode(executeCodeRequest);
    }
}
