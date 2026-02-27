package com.ff.fojsandbox.sandbox.cpp;

import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.sandbox.DockerCodeSandboxTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("cpp")
public class CppCodeSandboxImpl extends DockerCodeSandboxTemplate {

    private static final String GLOBAL_CPP_FILE_NAME = "main.cpp";
    private static final String IMAGE = "frolvlad/alpine-gxx";

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
        return IMAGE;
    }

    @Override
    protected String[] getCompileCommand() {
        return new String[]{"g++", "-O2", "-std=c++11","main.cpp", "-o", "main"};
    }

    @Override
    protected String[] getRunCommand() {
        return new String[]{"./main"};
    }
}
