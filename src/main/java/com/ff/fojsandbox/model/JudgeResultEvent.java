package com.ff.fojsandbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JudgeResultEvent {
    private String submissionId;
    private ExecuteCodeResponse response;
}
