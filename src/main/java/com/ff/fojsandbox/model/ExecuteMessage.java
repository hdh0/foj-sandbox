package com.ff.fojsandbox.model;

import lombok.Data;

@Data
public class ExecuteMessage {

    private Integer exitCode;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;
}
