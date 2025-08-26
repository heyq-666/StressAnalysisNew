package com.zxkkj.stressAnalysis.model;

import com.zxkkj.stressAnalysis.calculator.FCLPDetector;
import lombok.Data;

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
    List<Double> ecgList = new ArrayList<>();

    /**
     * 心率波
     */
    List<Double> hrList = new ArrayList<>();

    /**
     * 呼吸波
     */
    private List<Double> breathData = new ArrayList<>();

    /**
     * 呼吸率
     */
    List<Double[]> breathRate = new ArrayList<>();

    /**
     * 加速度
     */
    private List<Double[]> yzxData = new ArrayList<>();

    /**
     * fclp段
     */
    private List<FCLPDetector.FCLPSegment> fclpSegments = new ArrayList<>();

    /**
     * fclp个数
     */
    private int fclpNum;

    /**
     * 应激强度值
     */
    List<StressPoint> stressPoints = new ArrayList<>();

    /**
     * RR间期数组
     */
    List<Double> RRList = new ArrayList<>();

    Double[] fclpBeforeData = new Double[3];

    Double[] fclpAfterData = new Double[3];
}