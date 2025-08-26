package com.zxkkj.stressAnalysis.utils;

import java.util.function.Supplier;

/**
 * 辅助类用于存储输出配置
 */
public class OutputConfig {
    public String fileName;
    public Supplier<Object> dataSupplier;

    public OutputConfig(String fileName, Supplier<Object> dataSupplier) {
        this.fileName = fileName;
        this.dataSupplier = dataSupplier;
    }
}