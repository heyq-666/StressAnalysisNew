package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应激强度数据点类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StressPoint {
    private int startIndex;
    private int endIndex;
    private double stressValue;
    /**
     * 应激强度（高中低）
     */
    private int stressIntensityIndex;
}