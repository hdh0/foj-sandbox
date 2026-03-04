package com.ff.fojsandbox.sandbox.python;

import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.pool.ContainerPool;
import com.ff.fojsandbox.sandbox.DockerCodeSandboxTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component("python")
public class PythonCodeSandboxImpl extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_PYTHON_FILE_NAME = "main.py";

    @Resource
    private ContainerPool containerPool;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    @Override
    protected String getFileName() {
        return GLOBAL_PYTHON_FILE_NAME;
    }

    @Override
    protected boolean needCompile() {
        return false;
    }

    @Override
    protected String getImage() {
        return null;
    }

    @Override
    protected String[] getCompileCommand() {
        return null;
    }

    @Override
    protected String[] getRunCommand() {
        return new String[]{"timeout", "2s", "python3", "-u", "main.py"};
    }

    @Override
    public String createAndStartContainer(){
        try {
            // 从容器池请求一个容器
            return containerPool.borrowContainer("python");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanContainer(String containerId) {
        // 归还容器
        containerPool.returnContainer("python", containerId);
    }
}
