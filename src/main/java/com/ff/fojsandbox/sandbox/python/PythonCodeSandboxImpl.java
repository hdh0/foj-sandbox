package com.ff.fojsandbox.sandbox.python;

import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.DockerCodeSandboxTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("python")
public class PythonCodeSandboxImpl extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_PYTHON_FILE_NAME = "main.py";
    private static final String IMAGE = "python:3.11-alpine";

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
        return IMAGE;
    }

    @Override
    protected String[] getCompileCommand() {
        return null;
    }

    @Override
    protected String[] getRunCommand() {
        return new String[]{"python3", "-u", "main.py"};
    }
}
