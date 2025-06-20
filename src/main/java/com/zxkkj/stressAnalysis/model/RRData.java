package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author javabage
 * @date 2022/9/6
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RRData {

    private double RRIntervalData;
    private double hr;
    private Integer samplingNum;
}
