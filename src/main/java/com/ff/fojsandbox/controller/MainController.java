package com.ff.fojsandbox.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.ff.fojsandbox.config.SandboxConfig;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.CodeSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Slf4j
@RestController("/")
public class MainController {
    private static final long LOCK_EXPIRE_SECONDS = 5L;
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    @Resource
    private Map<String, CodeSandbox> codeSandboxMap;

    @Resource
    private SandboxConfig sandboxConfig;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "Auth";

    // @Value("${sandbox.api-key}")
    // private String AUTH_REQUEST_SECRET;

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
        if (StrUtil.isBlank(authHeader)) {
            response.setStatus(401);
            return null;
        }
        authHeader = Base64.decodeStr(authHeader);
        log.info("User: {}", authHeader);
        String key = "token:user:" + authHeader;
        String lockKey = "lock:token:user:" + authHeader;
        String lockValue = UUID.randomUUID().toString();
        Boolean lockSuccess = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, lockValue, LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS
        );
        if (!BooleanUtil.isTrue(lockSuccess)) {
            response.setStatus(429);
            return null;
        }
        try {
            String s = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(s)) {
                response.setStatus(401);
                return null;
            }
            int token;
            try {
                token = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                log.error("Invalid token value, user={}, value={}", authHeader, s, e);
                response.setStatus(500);
                return null;
            }
            if (token <= 0) {
                response.setStatus(403);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, String.valueOf(token - 1));
        } finally {
            DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>();
            unlockScript.setScriptText(UNLOCK_LUA);
            unlockScript.setResultType(Long.class);
            stringRedisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockValue);
        }
        // if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
        //     response.setStatus(403);
        //     return null;
        // }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        String language = executeCodeRequest.getLanguage();
        return codeSandboxMap.get(language).executeCode(executeCodeRequest);
    }
}
