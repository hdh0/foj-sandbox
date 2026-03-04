package com.ff.fojsandbox.sandbox.cpp;

import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.pool.ContainerPool;
import com.ff.fojsandbox.sandbox.DockerCodeSandboxTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component("cpp")
public class CppCodeSandboxImpl extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_CPP_FILE_NAME = "main.cpp";

    @Resource
    private ContainerPool containerPool;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    @Override
    protected String getFileName() {
        return GLOBAL_CPP_FILE_NAME;
    }

    @Override
    protected boolean needCompile() {
        return true;
    }

    @Override
    protected String getImage() {
        return null;
    }

    @Override
    protected String[] getCompileCommand() {
        return new String[]{"g++", "-O2", "-std=c++11","main.cpp", "-o", "main"};
    }

    @Override
    protected String[] getRunCommand() {
        return new String[]{"./main"};
    }

    @Override
    public String createAndStartContainer(){
        try {
            // 从容器池请求一个容器
            return containerPool.borrowContainer("cpp");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanContainer(String containerId) {
        // 归还容器
        containerPool.returnContainer("cpp", containerId);
    }
}
