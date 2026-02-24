package com.ff.fojsandbox.model;

import lombok.Data;

/**
 * 判题结果信息
 *
 * @author hdh
 */

@Data
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 时间
     */
    private Long time;

    /**
     * 内存
     */
    private Long memory;
}
