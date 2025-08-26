package com.zxkkj.stressAnalysis.calculator;

import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.model.EvaluatConclusion;
import com.zxkkj.stressAnalysis.model.StressPoint;
import com.zxkkj.stressAnalysis.utils.CommonUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StressValueCalculator {
    private final List<FCLPDetector.FCLPSegment> fclpSegments;
    private final List<StressPoint> fclpPoints;

    private List<EvaluatConclusion> conclusions;

    public StressValueCalculator(List<FCLPDetector.FCLPSegment> fclpSegments, List<StressPoint> fclpPoints) {
        this.fclpSegments = fclpSegments;
        this.fclpPoints = fclpPoints;
    }

    /**
     * 计算应激强度高中低
     */
    public void calculateStressValue() {
        if (!CollectionUtils.isEmpty(fclpSegments)){
            //先计算判断应激强度高中低的上线包线
            List<Double[]> EnvelopeList = CommonUtils.calculationEnvelope(Integer.valueOf(1));
            //根据应激强度高中低的上线包线来计算应激强度高、中、低
            List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,fclpPoints,fclpPoints.size(),1);
            this.conclusions = streeStatus;
        }
    }

    private List<EvaluatConclusion> calculatioStreeStatus(List<Double[]> envelopeList, List<StressPoint> stressList, int fclpSize, int fclpNum) {

        double standardY = envelopeList.get(0)[0];

        double upperY = envelopeList.get(0)[1];

        double lowerY = envelopeList.get(0)[2];

        List<EvaluatConclusion> streeStatusList = new ArrayList();
        EvaluatConclusion evaluatConclusion = new EvaluatConclusion();

        double variance = 0.0;
        for (int i = 0; i < stressList.size(); i++) {
            //应激强度低（中、高）
            double stressIntensity = stressList.get(i).getStressValue();
            if (stressIntensity > upperY){
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.high.getValue());
            }else if (stressIntensity <= upperY && stressIntensity >= lowerY){
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.middle.getValue());
            }else {
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.low.getValue());
            }
            //应激稳定度为高（中、低）
            variance = variance + Math.pow((stressList.get(i).getStressValue() - standardY),2);
            variance = new BigDecimal(variance).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        }
        //应激稳定度:对于一个或多个应激强度，求应激强度值与standardY的方差
        double stressIntensity = variance / stressList.size();
        if (stressIntensity - 1.0 < 0.0){
            evaluatConclusion.setStressStability(Constants.streeStatus.high.getValue());
        }else if (stressIntensity - 1.0 >= 0.0 && stressIntensity - 2.0 <= 0.0){
            evaluatConclusion.setStressStability(Constants.streeStatus.middle.getValue());
        }else {
            evaluatConclusion.setStressStability(Constants.streeStatus.low.getValue());
        }
        //应激适应度处于XXX阶段
        int t = 62;
        int fclpTotal = fclpSize + fclpNum;
        if (fclpTotal < t){
            evaluatConclusion.setStressFitness(Constants.streeStatus.low.getValue());
        }else if (fclpTotal >= t && fclpTotal <= 3 * t){
            evaluatConclusion.setStressFitness(Constants.streeStatus.middle.getValue());
        }else {
            evaluatConclusion.setStressFitness(Constants.streeStatus.high.getValue());
        }
        streeStatusList.add(evaluatConclusion);
        return streeStatusList;
    }

    public List<EvaluatConclusion> getEvaluatConclusions() {
        return conclusions;
    }
}
