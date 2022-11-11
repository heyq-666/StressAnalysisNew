package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * hrv指标
 * @author javabage
 * @date 2022/9/15
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HRVIndex {

    /**
     * 时域指标
     */
    private double SDNN;

    private double SDANN;

    private double SDNNIndex;

    private double RMSSD;

    private int NN50;

    private double PNN50;

    /**
     * 频域指标
     */
    private double HF;

    private double LF;

    private double LFNorm;

    private double HFNorm;

    private double LFAndHFRatio;
}
