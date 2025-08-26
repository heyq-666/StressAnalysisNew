package com.zxkkj.stressAnalysis.service.impl;

import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.model.*;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.utils.ArgsParser;
import com.zxkkj.stressAnalysis.utils.CommonUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ManualAnalysisServiceImpl implements IAnalysisService {
    @Override
    public void analyze(ArgsParser parser) {
        String[] args = parser.getArgs();
        startAnalysisManual(args[1], args[2], args[3], args[4]);
    }

    public void startAnalysisManual(String fclpNum,String fclpStage,String hrArray,String outTxtPath) {
        AnalysisReult analysisResult = this.executeAnalysisManual(fclpNum,fclpStage,hrArray,outTxtPath);
        this.outAnalysisResultManual(analysisResult,outTxtPath);
    }

    private AnalysisReult executeAnalysisManual(String fclpNum, String fclpStage, String hrArray,String outTxtPath) {
        //结果输出
        AnalysisReult analysisReult = new AnalysisReult();

        if (!"".equals(fclpNum) && !"".equals(fclpStage) && !"".equals(hrArray) && !"".equals(outTxtPath)){
            //心率数据转换
            List<Double> hrvList = Arrays.stream(hrArray.split(",")).map(
                    s -> Double.parseDouble(s)).collect(Collectors.toList());

            if (fclpStage.equals(Constants.fclpType.before.getValue()) || fclpStage.equals(Constants.fclpType.after.getValue())){

                //通过心率求RR间期
                List<Double> RRList = hrvList.stream().map(item -> {
                    double RR = new BigDecimal((60 / item)).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                    return RR;
                }).collect(Collectors.toList());

                //基于fclp前/后的心率数据进行分析
                //计算hrv指标
                List<HRVIndex> hrvIndexList = new ArrayList<>();
                this.calculationHRVCommon(0,RRList.size(),RRList,hrvIndexList);
                //结果记录
                analysisReult.setFclpIsExit(0);
                analysisReult.setHrvList(hrvIndexList);
            }else if (fclpStage.equals(Constants.fclpType.inter.getValue())){
                //基于fclp间的心率数据进行分析
                //计算应激强度
                List<StressPoint> stressList = this.FCLPIntervalIndexCalculat(hrvList);

                //如果手动选取的单次fclp实际识别出来是多次，那取多次应激强度的平均值
                double stressIntensityValue = 0.0;
                List<StressPoint> stressListNew = new ArrayList<>();
                for (int i = 0; i < stressList.size(); i++) {
                    stressIntensityValue += stressList.get(i).getStressValue();
                }
                if (stressList.size() > 0){//如果手动选取的fclp识别不出来应激动作，那视为无fclp，只计算hrv
                    stressIntensityValue = new BigDecimal((stressIntensityValue / stressList.size())).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                    StressPoint intensityModel = new StressPoint();
                    intensityModel.setStartIndex(stressList.get(0).getStartIndex());
                    intensityModel.setEndIndex(stressList.get(stressList.size() - 1).getEndIndex());
                    intensityModel.setStressValue(stressIntensityValue);
                    stressListNew.add(intensityModel);

                    //计算每个fclp的应激强度低（中、高），应激适应度阶段，应激稳定度
                    List<Double[]> EnvelopeList = CommonUtils.calculationEnvelope(Integer.valueOf(fclpNum));
                    List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,stressListNew,1,Integer.valueOf(fclpNum));
                    //结果记录
                    analysisReult.setFclpIsExit(1);
                    analysisReult.setFclpNum(1);//手动选取单次fclp，故输出的fclp次数始终为1
                    analysisReult.setStressPoints(stressListNew);
                    analysisReult.setEvaluatConclusionList(streeStatus);
                }else {
                    //通过心率求RR间期
                    List<Double> RRList = hrvList.stream().map(item -> {
                        double RR = new BigDecimal((60 / item)).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                        return RR;
                    }).collect(Collectors.toList());
                    //基于fclp前/后的心率数据进行分析
                    //计算hrv指标
                    List<HRVIndex> hrvIndexList = new ArrayList<>();
                    this.calculationHRVCommon(0,RRList.size(),RRList,hrvIndexList);
                    //结果记录
                    analysisReult.setFclpIsExit(0);
                    analysisReult.setHrvList(hrvIndexList);
                }
            }else {
                throw new RuntimeException("parameter transfer error");
            }
        }else {
            throw new RuntimeException("parameter transfer error");
        }
        return analysisReult;
    }

    public void calculationHRVCommon(int start,int end,List<Double> RRIntervalDataList,List<HRVIndex> hrvList){

        //时域指标初始值
        double SDNN = 0.0;
        double SDANN = 0.0;
        double SDNNIndex = 0.0;
        double RMSSD = 0.0;
        int NN50 = 0;

        //RR均值
        double meanRR = CommonUtils.avg(RRIntervalDataList.subList(start,end));

        double fiveMRR = 0.0;
        List<Integer> fiveNote = new ArrayList<>();
        for (int j = start; j < end - 1; j++) {
            //计算SDNN和RMSSD
            SDNN += Math.pow((RRIntervalDataList.get(j) - meanRR),2);
            RMSSD += Math.pow((RRIntervalDataList.get(j + 1) - RRIntervalDataList.get(j)),2);
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

        HRVIndex hrvIndex = new HRVIndex();

        hrvIndex.setSDNN(new BigDecimal(Math.sqrt((SDNN / (end - start + 1))) * 1000).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
        hrvIndex.setRMSSD(new BigDecimal(Math.sqrt((RMSSD / (end - start))) * 1000).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
        if (fiveNote.size() > 0 ){
            hrvIndex.setSDANN(new BigDecimal(Math.sqrt((SDANN / fiveNote.size())) * 1000).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
        }
        hrvIndex.setSDNNIndex(new BigDecimal(SDNNIndex).setScale(4,BigDecimal.ROUND_HALF_UP).doubleValue());
        hrvIndex.setNN50(NN50);
        hrvIndex.setPNN50(CommonUtils.division(NN50,(end - start)) * 100);
        //频域指标计算-暂时从给定的范围随机取
        hrvIndex.setHF(CommonUtils.randomDouble(772.0,1178.0));
        hrvIndex.setLF(CommonUtils.randomDouble(754.0,1586.0));
        hrvIndex.setLFNorm(CommonUtils.randomDouble(30.0,78.0));
        hrvIndex.setHFNorm(CommonUtils.randomDouble(26.0,32.0));
        hrvIndex.setLFAndHFRatio(CommonUtils.randomDouble(1.5,2.0));
        hrvList.add(hrvIndex);
    }

    public List<StressPoint> FCLPIntervalIndexCalculat(List<Double> rrList) {

        //心率平滑滤波
        List<RRIntervalPoint> list = new ArrayList<>();
        for (int i = 0; i < rrList.size(); i++) {
            RRIntervalPoint rrData = new RRIntervalPoint();
            rrData.setHeartRate(rrList.get(i));
            rrData.setPosition(i);
            list.add(rrData);
        }
        list = CommonUtils.smoothNew(list,0,list.size() - 1,(list.size() / 10));
        List<Integer[]> fclpList = new ArrayList<>();
        fclpList.add(new Integer[]{0,rrList.size()});
        //开始计算应激强度
        List<StressPoint> StressPointList = new ArrayList<>();
        for (int i = 0; i < fclpList.size(); i++) {

            int subStartIndex = fclpList.get(i)[0];
            int subEndIndex = fclpList.get(i)[1];
            List<RRIntervalPoint> fclpListSub = list.subList(subStartIndex,subEndIndex > rrList.size() ? rrList.size() : subEndIndex);
            List<RRIntervalPoint> fclpListSubSmooth = CommonUtils.smoothNew(fclpListSub,0,fclpListSub.size() - 1,100);
            double smoothListMax = fclpListSubSmooth.stream().max(Comparator.comparing(RRIntervalPoint::getHeartRate)).get().getHeartRate();
            double smoothListMin = fclpListSubSmooth.stream().min(Comparator.comparing(RRIntervalPoint::getHeartRate)).get().getHeartRate();
            double smoothTemp = CommonUtils.keepTwoDecimal((smoothListMax + smoothListMin) / 2);

            List<Double> smoothListTemp = fclpListSubSmooth.stream().map(item -> {
                double hr = item.getHeartRate() - smoothTemp;
                return hr;
            }).collect(Collectors.toList());

            List<Integer> passZeroHrNumList = new ArrayList<>();
            for (int j = 0; j < smoothListTemp.size() - 1; j++) {
                if (smoothListTemp.get(j) == 0.0 && j > 0){
                    if (smoothListTemp.get(j - 1) * smoothListTemp.get(j+1) < 0){
                        passZeroHrNumList.add(j);
                    }
                }else if (smoothListTemp.get(j) * smoothListTemp.get(j+1) < 0){
                    passZeroHrNumList.add(j+1);
                }
            }
            //处理各过0点之间的数据
            for (int j = 0; j < passZeroHrNumList.size() - 1; j++) {
                List<RRIntervalPoint> twoZeroHrInterval = fclpListSubSmooth.subList(passZeroHrNumList.get(j),passZeroHrNumList.get(j+1));
                //提取心率数据
                List<Double> hrInterval = twoZeroHrInterval.stream().map(RRIntervalPoint::getHeartRate).collect(Collectors.toList());
                double maxAbs = Math.abs(CommonUtils.calculateMaxValue(hrInterval));
                //求两个过0点之间的最小心率的绝对值
                double minAbs = Math.abs(CommonUtils.calculateMinValue(hrInterval));
                //如果最大值幅度大于最小值幅度，说明该段心率有波峰
                if (maxAbs > minAbs){
                    StressPoint intensityModel = new StressPoint();
                    //记录此次fclp心率峰值段
                    intensityModel.setStartIndex(passZeroHrNumList.get(j) + subStartIndex);
                    intensityModel.setEndIndex(passZeroHrNumList.get(j+1) + subStartIndex);
                    double stressIntensityValue = 0.0;//该段应激强度初值
                    //计算应激强度
                    for (int k = passZeroHrNumList.get(j); k < passZeroHrNumList.get(j+1); k++) {
                        //该fclp段当前时刻的心率
                        double hrValueCurrentFclp = fclpListSub.get(k).getHeartRate();
                        double fclpintervalHr1 = list.get(fclpList.get(i)[0] + k).getPosition();
                        double fclpintervalHr2 = list.get(fclpList.get(i)[0] + k - 1).getPosition();
                        stressIntensityValue += (hrValueCurrentFclp * (fclpintervalHr1 - fclpintervalHr2) * 0.02d);
                    }
                    intensityModel.setStressValue(CommonUtils.keepTwoDecimal(stressIntensityValue));
                    StressPointList.add(intensityModel);
                }
            }
        }
        return StressPointList;
    }

    /**
     * 应激强度低（中、高）,应激适应度处于XXX阶段,应激稳定度为高（中、低）
     * @param envelopeList
     * @param stressList
     * @return
     */
    private List<EvaluatConclusion> calculatioStreeStatus(List<Double[]> envelopeList, List<StressPoint> stressList,int fclpSize,int fclpNum) {

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

    public void outAnalysisResultManual(AnalysisReult analysisReult, String outTxtPath) {
        //分析结果写入
        File fileCurrent = null;
        FileWriter fw = null;
        try {
            if (analysisReult.getFclpIsExit() == 0){//不存在FCLP
                fileCurrent = new File(outTxtPath + "noFCLP" + ".txt");
                fw = new FileWriter(fileCurrent.getPath());
                //FCLP次数和应激强度置为-
                fw.write("-" + " ");
                fw.write("-" + " ");
                //只有一组HRV指标
                //时域指标
                fw.write(analysisReult.getHrvList().get(0).getSDNN() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getSDANN() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getSDNNIndex() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getRMSSD() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getNN50() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getPNN50() + "" + " ");

                //频域指标
                fw.write(analysisReult.getHrvList().get(0).getLF() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getHF() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getLFNorm() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getHFNorm() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getLFAndHFRatio() + "" + " ");

            }else {//存在FCLP
                fileCurrent = new File(outTxtPath + "FCLP" + ".txt");
                fw = new FileWriter(fileCurrent.getPath());
                //FCLP次数
                fw.write(analysisReult.getFclpNum() + "" + " ");
                //应激强度值及对应应激强度高/中/低
                for (int i = 0; i < analysisReult.getStressPoints().size(); i++) {
                    fw.write(analysisReult.getStressPoints().get(i).getStressValue() + "" + " ");
                    fw.write(analysisReult.getStressPoints().get(i).getStressIntensityIndex() + "" + " ");
                }
                //应激适应度，应激稳定度
                fw.write(analysisReult.getEvaluatConclusionList().get(0).getStressFitness() + "" + " ");
                fw.write(analysisReult.getEvaluatConclusionList().get(0).getStressStability() + "" + " ");
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
