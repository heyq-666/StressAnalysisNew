package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分析结果
 */
@Data
public class AnalysisReult implements Serializable {

    public int fclpIsExit;//1：存在fclp、0：不存在fclp

    /**
     * 非fclp阶段hrv
     */
    List<HRVIndex> hrvList = new ArrayList<>();

    /**
     * 应激适应度阶段，应激稳定度
     */
    List<EvaluatConclusion> evaluatConclusionList = new ArrayList<>();

    /**
     * 心电波
     */
    List<Integer> ecgList = new ArrayList<>();

    /**
     * 心率波
     */
    List<Double> hrList = new ArrayList<>();

    /**
     * fclp段
     */
    List<Integer[]> fclpList = new ArrayList<>();

    /**
     * fclp个数
     */
    private int fclpNum;

    /**
     * 应激强度值
     */
    List<StressIntensityModel> stressIntensityModelList = new ArrayList<>();

    /**
     * RR间期数组
     */
    List<Double> RRList = new ArrayList<>();
}
