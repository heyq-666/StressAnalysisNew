package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author javabage
 * @date 2022/9/7
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StressIntensityModel {

    /**
     * 心率起始时间
     */
    private Integer startTime;
    /**
     * 心率结束时间
     */
    private Integer endTime;
    /**
     * 应激强度值
     */
    private double stressIntensityValue;
    /**
     * 应激强度（高中低）
     */
    private int stressIntensityIndex;
}
