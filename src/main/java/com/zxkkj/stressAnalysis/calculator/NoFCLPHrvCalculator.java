package com.zxkkj.stressAnalysis.calculator;

import com.zxkkj.stressAnalysis.model.HRVIndex;
import com.zxkkj.stressAnalysis.model.RRIntervalPoint;
import com.zxkkj.stressAnalysis.model.StressPoint;
import com.zxkkj.stressAnalysis.utils.CommonUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NoFCLPHrvCalculator {
    private final List<FCLPDetector.FCLPSegment> fclpSegments;
    private final List<StressPoint> stressPoints;
    private final List<RRIntervalPoint> rrIntervals;

    private Double[] fclpBeforeData;

    private Double[] fclpAfterData;

    private List<HRVIndex> hrvList = new ArrayList<>();


    public NoFCLPHrvCalculator(List<FCLPDetector.FCLPSegment> fclpSegments, List<StressPoint> stressPoints, List<RRIntervalPoint> rrIntervals) {
        this.fclpSegments = fclpSegments;
        this.stressPoints = stressPoints;
        this.rrIntervals = rrIntervals;
    }

    public Double[] getFclpBeforeData() {
        return fclpBeforeData;
    }

    public Double[] getFclpAfterData() {
        return fclpAfterData;
    }

    public List<HRVIndex> getHrvList() {
        return hrvList;
    }

    public void noFCLPHrvCalculator() {
        List<Integer[]> fclpNo = new ArrayList<>();

        if (CollectionUtils.isEmpty(fclpSegments)){
            fclpNo.add(new Integer[]{0,rrIntervals.size() - 1});
            //fclp前的心率均值、最大值、最小值
            List<RRIntervalPoint> listBeforeFclp = rrIntervals.subList(fclpNo.get(0)[0],fclpNo.get(0)[1]);
            double fclpBeforeMean = CommonUtils.mean1(listBeforeFclp);
            double fclpBeforeMax = listBeforeFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).max().getAsDouble();
            double fclpBeforeMin = listBeforeFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).min().getAsDouble();
            this.fclpBeforeData = new Double[]{fclpBeforeMean,fclpBeforeMax,fclpBeforeMin};
        }else {
            fclpNo.add(new Integer[]{0,stressPoints.get(0).getStartIndex() - 1});
            fclpNo.add(new Integer[]{stressPoints.get(stressPoints.size()-1).getEndIndex() + 1,rrIntervals.size() - 1});
            //fclp前的心率均值、最大值、最小值
            List<RRIntervalPoint> listBeforeFclp = rrIntervals.subList(fclpNo.get(0)[0],fclpNo.get(0)[1]);
            double fclpBeforeMean = CommonUtils.mean1(listBeforeFclp);
            double fclpBeforeMax = listBeforeFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).max().getAsDouble();
            double fclpBeforeMin = listBeforeFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).min().getAsDouble();
            Double[] fclpBeforeData = new Double[]{fclpBeforeMean,fclpBeforeMax,fclpBeforeMin};

            //fclp后的心率均值、最大值、最小值
            List<RRIntervalPoint> listAfterFclp = rrIntervals.subList(fclpNo.get(1)[0],fclpNo.get(1)[1]);
            double fclpAfterMean = CommonUtils.mean1(listAfterFclp);
            double fclpAfterMax = listAfterFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).max().getAsDouble();
            double fclpAfterMin = listAfterFclp.stream().mapToDouble(RRIntervalPoint::getHeartRate).min().getAsDouble();
            Double[] fclpAfterData = new Double[]{fclpAfterMean,fclpAfterMax,fclpAfterMin};

            /*analysisReult.setFclpBeforeData(fclpBeforeData);
            analysisReult.setFclpAfterData(fclpAfterData);*/
        }
        //提取RR间期数组
        List<Double> RRIntervalDataList = rrIntervals.stream().map(RRIntervalPoint::getRrInterval).collect(Collectors.toList());
        //开始计算hrv指标
        for (int i = 0; i < fclpNo.size(); i++) {
            //HRV段的RR均值
            double meanRR = CommonUtils.avg(RRIntervalDataList.subList(fclpNo.get(i)[0],fclpNo.get(i)[1]));
            double SDNN = 0.0;
            double RMSSD = 0.0;
            double SDANN = 0.0;
            double SDNNIndex = 0.0;
            int NN50 = 0;
            double fiveMRR = 0.0;
            List<Integer> fiveNote = new ArrayList<>();
            for (int j = fclpNo.get(i)[0]; j < fclpNo.get(i)[1]; j++) {
                SDNN += Math.pow(rrIntervals.get(j).getRrInterval() - meanRR,2);
                if (j < fclpNo.get(i)[1]){
                    RMSSD += Math.pow(rrIntervals.get(j + 1).getRrInterval()- rrIntervals.get(j).getRrInterval(),2);
                }
                //划分5分钟RR间期
                fiveMRR += RRIntervalDataList.get(j);
                if (fiveMRR >= 300){
                    fiveMRR = 0.0;
                    fiveNote.add(j);
                }
                //NN50计算
                if ((RRIntervalDataList.get(j + 1) - RRIntervalDataList.get(j)) > 0.05 ){
                    NN50 += 1;
                }
            }
            HRVIndex hrvIndex = new HRVIndex();
            hrvIndex.setSDNN(CommonUtils.keepTwoDecimal(Math.sqrt((SDNN / (fclpNo.get(i)[1] - fclpNo.get(i)[0] + 1))) * 1000));
            hrvIndex.setRMSSD(CommonUtils.keepTwoDecimal(Math.sqrt((RMSSD / (fclpNo.get(i)[1] - fclpNo.get(i)[0]))) * 1000));
            hrvIndex.setNN50(NN50);

            List<Double> RRqList = new ArrayList<>();
            double RRqTotal = 0.0;
            List<Double> SDNNIndexFiveList = new ArrayList<>();
            for (int j = 0; j < fiveNote.size() - 1; j++) {
                double SDNNIndexFive = 0.0;
                double fiveIndexAvg = 0.0;
                if (j == 0){
                    //计算SDANN过程
                    double RRq = CommonUtils.avg(RRIntervalDataList.subList(0,fiveNote.get(j)));//5分钟RR均值
                    RRqList.add(RRq);
                    RRqTotal += RRq;
                    //计算SDNNIndex过程
                    for (int k = 0; k < fiveNote.get(j); k++) {
                        SDNNIndexFive = Math.pow((RRIntervalDataList.get(k) - RRq),2);
                    }
                }else {
                    double RRq = CommonUtils.avg(RRIntervalDataList.subList(fiveNote.get(j),fiveNote.get(j + 1)));
                    RRqList.add(RRq);
                    RRqTotal += RRq;
                    //计算SDNNIndex过程
                    for (int k = fiveNote.get(j); k < fiveNote.get(j + 1); k++) {
                        SDNNIndexFive = Math.pow((RRIntervalDataList.get(k) - RRq),2);
                    }
                }
                fiveIndexAvg = SDNNIndexFive / fiveNote.get(j);
                //每5分钟内RR间期标准差
                fiveIndexAvg = (Math.sqrt(fiveIndexAvg)) * 1000;
                SDNNIndexFiveList.add(fiveIndexAvg);
            }
            for (int j = 0; j < SDNNIndexFiveList.size(); j++) {
                SDNNIndex += SDNNIndexFiveList.get(j);
            }
            if (fiveNote.size() > 0){
                SDNNIndex = SDNNIndex / fiveNote.size();
            }
            double RRFiveMin = RRqTotal / fiveNote.size();
            for (int j = 0; j < RRqList.size(); j++) {
                SDANN += Math.pow((RRqList.get(j) - RRFiveMin),2);
            }

            if (fiveNote.size() > 0 ){
                hrvIndex.setSDANN(new BigDecimal(Math.sqrt((SDANN / fiveNote.size())) * 1000).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
            }

            hrvIndex.setSDNNIndex(new BigDecimal(SDNNIndex).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
            hrvIndex.setNN50(NN50);
            hrvIndex.setPNN50(CommonUtils.division(NN50,(fclpNo.get(i)[1] - fclpNo.get(i)[0])) * 100);
            //频域指标计算-暂时从给定的范围随机取
            hrvIndex.setHF(CommonUtils.randomDouble(772.0,1178.0));
            hrvIndex.setLF(CommonUtils.randomDouble(754.0,1586.0));
            hrvIndex.setLFNorm(CommonUtils.randomDouble(30.0,78.0));
            hrvIndex.setHFNorm(CommonUtils.randomDouble(26.0,32.0));
            hrvIndex.setLFAndHFRatio(CommonUtils.randomDouble(1.5,2.0));
            this.hrvList.add(hrvIndex);
        }
    }
}
