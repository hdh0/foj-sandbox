package com.ff.fojsandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;
import com.ff.fojsandbox.model.ExecuteMessage;
import com.ff.fojsandbox.model.JudgeInfo;
import com.ff.fojsandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class JavaNativeCodeSandboxImpl extends JavaCodeSandboxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
