package com.ff.fojsandbox;


import com.ff.fojsandbox.model.ExecuteCodeRequest;
import com.ff.fojsandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口
 *
 * @author hdh
 */
public interface CodeSandbox {
    /**
     * 执行代码
     *
     * @param executeCodeRequest 包含代码、输入和语言等信息的请求对象
     * @return 包含代码执行结果的响应对象
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

    // todo : 查看沙箱状态接口
}
