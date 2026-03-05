package com.ff.fojsandbox.controller;

import com.ff.fojsandbox.config.SandboxConfig;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.CodeSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController("/")
public class MainController {

    @Resource
    private Map<String, CodeSandbox> codeSandboxMap;

    @Resource
    private SandboxConfig sandboxConfig;

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "Auth";

    @Value("${sandbox.api-key}")
    private String AUTH_REQUEST_SECRET;

    @GetMapping("/ping")
    public String checkHealth() {
        System.out.println("SandboxConfig: " + sandboxConfig);
        return "pong";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                    HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        String language = executeCodeRequest.getLanguage();
        return codeSandboxMap.get(language).executeCode(executeCodeRequest);
    }
}
