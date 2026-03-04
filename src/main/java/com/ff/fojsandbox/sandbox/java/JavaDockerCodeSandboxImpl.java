package com.ff.fojsandbox.sandbox.java;

import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.pool.ContainerPool;
import com.ff.fojsandbox.sandbox.DockerCodeSandboxTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("java")
public class JavaDockerCodeSandboxImpl extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Resource
    private ContainerPool containerPool;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    @Override
    protected String getFileName() {
        return GLOBAL_JAVA_CLASS_NAME;
    }

    @Override
    protected boolean needCompile() {
        return true;
    }

    @Override
    protected String getImage() {
        return "";
    }

    @Override
    protected String[] getCompileCommand() {
        return new String[]{"javac", "-encoding", "utf-8", "Main.java"};
    }

    @Override
    protected String[] getRunCommand() {
        return new String[]{"timeout", "2s", "java", "Main"};
    }

    @Override
    public String createAndStartContainer(){
        try {
            // 从容器池请求一个容器
            return containerPool.borrowContainer("java");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanContainer(String containerId) {
        // 归还容器
        containerPool.returnContainer("java", containerId);
    }
}
