package com.zxkkj.stressAnalysis.model;

import lombok.Data;

@Data
public class ExecuteResult {

    /**
     * 读取文件成功数
     */
    private int successCount;

    /**
     * 读取文件失败数
     */
    private int failCount;

}
