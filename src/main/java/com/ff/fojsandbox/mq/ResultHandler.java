package com.ff.fojsandbox.mq;

import com.ff.fojsandbox.model.ExecuteCodeResponse;

public interface ResultHandler {
    /**
     * 处理判题结果方法
     * @param submissionId 提交ID
     * @param response 判题结果响应对象
     */
    void handleResult(String submissionId, ExecuteCodeResponse response);
}
