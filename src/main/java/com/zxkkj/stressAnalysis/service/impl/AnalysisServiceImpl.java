package com.zxkkj.stressAnalysis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.csv.CsvWriter;
import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.model.*;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AnalysisServiceImpl implements IAnalysisService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    //头标志字节
    private final int headByte1 = 85;
    private final int headByte2 = 170;
    //判断fclp所用指标
    private final double fclpIndex = (1.1 * 60) / 0.02;
    private final double fclpIndex1 = (1.3 * 60) / 0.02;
    private final double fclpIndex2 = (4.5 * 60) / 0.02;

    private final int segment1 = 1500;//段落心率数
    private final int segment0 = 50;//段落递进心率数

    @Override
    public List<Integer> loadDataByLoaclFile(File file) throws IOException {

        BufferedInputStream in = null;
        ByteArrayOutputStream out = null;
        byte[] content = null;
        List<Integer> list = new ArrayList();
        try {
            long before = System.currentTimeMillis();
            in = new BufferedInputStream(new FileInputStream(file));
            out = new ByteArrayOutputStream(1024);
            byte[] temp = new byte[1024];
            int size = 0;
            while((size = in.read(temp)) != -1){
                out.write(temp, 0, size);
            }
            content = out.toByteArray();
            //byte数组转为int存入list
            for (int i = 0; i < content.length; i++) {
                list.add(content[i] & 0xFF);
            }
            long after = System.currentTimeMillis();
            logger.info("读取并转换数据的耗时："+(after - before));
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            in.close();
        }
        return list;
    }

    @Override
    public AnalysisReult executeAnalysis(List<Integer> contentList,File file) {

        //结果输出
        AnalysisReult analysisReult = new AnalysisReult();

        //提取心电波形数据
        List<EcgHrData> ecgHrDataList = this.getEcgWaveData(contentList,file);
        //存储心电波
        analysisReult.setEcgList(ecgHrDataList.get(0).getEcgList());

        //计算RR间期和心率
        List<RRData> RRList = this.calculationRRAndHr(ecgHrDataList);
        List<Double> RRDataList = RRList.stream().map(RRData::getRRIntervalData).collect(Collectors.toList());
        analysisReult.setRRList(RRDataList);

        //提取每秒的心率数据
        List<Double> hrList = this.getHrList(RRList);
        analysisReult.setHrList(hrList);
        //FCLP段识别
        List<Integer[]> fclpList = this.FCLPIdentity(RRList);

        //非FCLP段HRV计算
        List<HRVIndex> hrvList = new ArrayList<>();

        analysisReult.setFclpList(fclpList);
        List<StressIntensityModel> stressList = new ArrayList<>();

        //FCLP个数统计
        int fclp = 0;
        double calculationIndex = 0.02d;
        if (fclpList != null && fclpList.size() > 0){//有fclp才计算fclp应激强度
            //FCLP段应激强度计算
            stressList = this.FCLPStressIntensity(fclpList,RRList,calculationIndex);
            fclp += stressList.size();
            //根据前次的fclp确定应激强度上包线值，下包线值，标准包线值，从而确定应激强度范围，应激适应度阶段，应激稳定度范围
            //暂定前次fclp序号为1，后续以传入文件中的fclp序号为准
            List<Double[]> EnvelopeList = this.calculationEnvelope(1);
            //计算每个fclp的应激强度低（中、高），应激适应度阶段，应激稳定度
            List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,stressList,fclpList.size(),1);
            //非FCLP段HRV计算
            hrvList = this.calculationHRV(fclpList,RRList);
            analysisReult.setStressIntensityModelList(stressList);//应激强度值及对应的起始结束时刻
            analysisReult.setEvaluatConclusionList(streeStatus);
            analysisReult.setFclpIsExit(1);
        }else {
            hrvList = this.calculationHRV(fclpList,RRList);
            analysisReult.setFclpIsExit(0);
        }

        analysisReult.setHrvList(hrvList);
        analysisReult.setFclpNum(fclp);
        return analysisReult;
    }

    @Override
    public AnalysisReult executeAnalysisManual(String fclpNum, String fclpStage, String hrArray,String outTxtPath) {

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
                List<StressIntensityModel> stressList = this.FCLPIntervalIndexCalculat(hrvList);

                //如果手动选取的单次fclp实际识别出来是多次，那取多次应激强度的平均值
                double stressIntensityValue = 0.0;
                List<StressIntensityModel> stressListNew = new ArrayList<>();
                for (int i = 0; i < stressList.size(); i++) {
                    stressIntensityValue += stressList.get(i).getStressIntensityValue();
                }
                stressIntensityValue = new BigDecimal((stressIntensityValue / stressList.size())).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                StressIntensityModel intensityModel = new StressIntensityModel();
                intensityModel.setStartTime(stressList.get(0).getStartTime());
                intensityModel.setEndTime(stressList.get(stressList.size() - 1).getEndTime());
                intensityModel.setStressIntensityValue(stressIntensityValue);
                stressListNew.add(intensityModel);

                //计算每个fclp的应激强度低（中、高），应激适应度阶段，应激稳定度
                List<Double[]> EnvelopeList = this.calculationEnvelope(Integer.valueOf(fclpNum));
                List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,stressListNew,1,Integer.valueOf(fclpNum));
                //结果记录
                analysisReult.setFclpIsExit(1);
                //analysisReult.setFclpNum(stressList.size());
                analysisReult.setFclpNum(1);//手动选取单次fclp，故输出的fclp次数始终为1
                analysisReult.setStressIntensityModelList(stressListNew);
                analysisReult.setEvaluatConclusionList(streeStatus);
            }else {
                throw new RuntimeException("parameter transfer error");
            }
        }else {
            throw new RuntimeException("parameter transfer error");
        }
        return analysisReult;
    }

    /**
     * 计算每秒心率集合
     * @param rrList
     * @return
     */
    private List<Double> getHrList(List<RRData> rrList) {

        //复制list
        List<RRData> rrListNew = rrList.stream().map(e -> {
            RRData rrData = new RRData();
            BeanUtil.copyProperties(e,rrData);
            return rrData;
        }).collect(Collectors.toList());
        //心率对应时间取整
        rrListNew.forEach(rrData -> rrData.setSamplingNum(new Double(rrData.getSamplingNum() * 0.02).intValue()));
        //根据取整后的时间取整
        List<RRData> newList = rrListNew.stream().collect(
                Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(RRData::getSamplingNum))), ArrayList::new)
        );
        //缺失时间段（秒）的心率插值(前后平均值)
        List<RRData> endList = new ArrayList<>();
        for (int i = 0; i < newList.size() - 1; i++) {
            if (newList.get(i + 1).getSamplingNum() - newList.get(i).getSamplingNum() > 1){
                for (int j = 0; j < (newList.get(i + 1).getSamplingNum() - newList.get(i).getSamplingNum()); j++) {
                    RRData rrData = new RRData();
                    rrData.setSamplingNum(newList.get(i).getSamplingNum() + j);
                    rrData.setHr((newList.get(i).getHr() + newList.get(i + 1).getHr()) / 2);
                    endList.add(rrData);
                }
            }else {
                RRData rrData = new RRData();
                rrData.setSamplingNum(newList.get(i).getSamplingNum());
                rrData.setHr(newList.get(i).getHr());
                endList.add(rrData);
            }
        }
        //输出心率数组，以产生心率的时间为第一时间
        List<Double> hrList = new ArrayList<>();
        for (int i = 0; i < endList.size(); i++) {
            hrList.add(endList.get(i).getHr());
        }
        return hrList;
    }

    @Override
    public void outAnalysisResult(AnalysisReult analysisReult, String outTxtPath, File file) {
        //分析结果写入
        File fileCurrent = new File(outTxtPath + file.getName().substring(0,file.getName().lastIndexOf(".")) + ".txt");
        FileWriter fw = null;
        try {
            //文件不存在才创建
            if (!fileCurrent.exists()){
                fileCurrent.createNewFile();
            }
            fw = new FileWriter(fileCurrent.getPath());
            fw.write(analysisReult.getFclpIsExit() + "" + " ");
            if (analysisReult.getFclpIsExit() == 1){//存在fclp

                //FCLP次数
                fw.write(analysisReult.getFclpNum() + "" + " ");
                //应激强度值及对应应激强度高/中/低
                for (int i = 0; i < analysisReult.getStressIntensityModelList().size(); i++) {
                    fw.write(analysisReult.getStressIntensityModelList().get(i).getStressIntensityValue() + "" + " ");
                    fw.write(analysisReult.getStressIntensityModelList().get(i).getStressIntensityIndex() + "" + " ");
                }
                //应激适应度，应激稳定度
                fw.write(analysisReult.getEvaluatConclusionList().get(0).getStressFitness() + "" + " ");
                fw.write(analysisReult.getEvaluatConclusionList().get(0).getStressStability() + "" + " ");
                //HRV时域指标
                for (int i = 0; i < analysisReult.getHrvList().size(); i++) {
                    //时域指标
                    fw.write(analysisReult.getHrvList().get(i).getSDNN() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getSDANN() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getSDNNIndex() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getRMSSD() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getNN50() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getPNN50() + "" + " ");
                    //频域指标
                    fw.write(analysisReult.getHrvList().get(i).getLF() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getHF() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getLFNorm() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getHFNorm() + "" + " ");
                    fw.write(analysisReult.getHrvList().get(i).getLFAndHFRatio() + "" + " ");
                }

            }else {
                //FCLP次数
                fw.write(analysisReult.getFclpNum() + "" + " ");
                //应激强度值为0
                fw.write(0 + "" + " ");
                //第一组时域指标
                fw.write(analysisReult.getHrvList().get(0).getSDNN() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getSDANN() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getSDNNIndex() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getRMSSD() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getNN50() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getPNN50() + "" + " ");

                //第一组频域指标
                fw.write(analysisReult.getHrvList().get(0).getLF() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getHF() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getLFNorm() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getHFNorm() + "" + " ");
                fw.write(analysisReult.getHrvList().get(0).getLFAndHFRatio() + "" + " ");

                //由于非fclp，只有一组HRV指标，所以第二组时域频域指标全置为"-"
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");

                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
                fw.write("-" + " ");
            }
            //将腰带源数据提取的心电心率输出至指定位置
            this.outECGAndHrListToCsv(analysisReult,outTxtPath);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logger.info(
                "\n"+"是否有fclp:"+analysisReult.getFclpIsExit()
        );
    }

    /**
     * 输出心电波和心率波数据至csv文件
     * @param analysisReult
     */
    private void outECGAndHrListToCsv(AnalysisReult analysisReult,String outTxtPath) {

        CsvWriter writer = null;
        CsvWriter writer1 = null;
        CsvWriter writer2 = null;
        try {
            //心电波输出
            FileOutputStream fileOutputStream = new FileOutputStream(outTxtPath + "ecg" + ".csv");
            fileOutputStream.write(0xef);
            fileOutputStream.write(0xbb);
            fileOutputStream.write(0xbf);
            List<Integer> ecgList = analysisReult.getEcgList();
            writer = new CsvWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8.name()));
            writer.write(ecgList);
            //心率波输出
            FileOutputStream fileOutputStream1 = new FileOutputStream(outTxtPath + "hr" + ".csv");
            fileOutputStream1.write(0xef);
            fileOutputStream1.write(0xbb);
            fileOutputStream1.write(0xbf);
            List<Double> hrList = analysisReult.getHrList();
            writer1 = new CsvWriter(new OutputStreamWriter(fileOutputStream1, StandardCharsets.UTF_8.name()));
            writer1.write(hrList);
            //RR间期输出
            FileOutputStream fileOutputStream2 = new FileOutputStream(outTxtPath + "RR" + ".csv");
            fileOutputStream2.write(0xef);
            fileOutputStream2.write(0xbb);
            fileOutputStream2.write(0xbf);
            List<Double> RRList = analysisReult.getRRList();
            writer2 = new CsvWriter(new OutputStreamWriter(fileOutputStream2, StandardCharsets.UTF_8.name()));
            writer2.write(RRList);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            writer.close();
            writer1.close();
            writer2.close();
        }
    }

    @Override
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
                for (int i = 0; i < analysisReult.getStressIntensityModelList().size(); i++) {
                    fw.write(analysisReult.getStressIntensityModelList().get(i).getStressIntensityValue() + "" + " ");
                    fw.write(analysisReult.getStressIntensityModelList().get(i).getStressIntensityIndex() + "" + " ");
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

    /**
     * FCLP间期应激强度计算
     * @param rrList
     * @return
     */
    public List<StressIntensityModel> FCLPIntervalIndexCalculat(List<Double> rrList) {

        //心率平滑滤波
        List<RRData> list = new ArrayList<>();
        for (int i = 0; i < rrList.size(); i++) {
            RRData rrData = new RRData();
            rrData.setHr(rrList.get(i));
            rrData.setSamplingNum(i);
            list.add(rrData);
        }

        list = CommonUtils.smoothNew(list,0,list.size() - 1,(list.size() / 10));
        List<Integer[]> fclpList = new ArrayList<>();
        fclpList.add(new Integer[]{0,rrList.size()});
        List<StressIntensityModel> stressIntensityModelList = this.FCLPStressIntensity(fclpList,list,1);
        return stressIntensityModelList;
    }

    /**
     * 应激强度低（中、高）,应激适应度处于XXX阶段,应激稳定度为高（中、低）
     * @param envelopeList
     * @param stressList
     * @return
     */
    private List<EvaluatConclusion> calculatioStreeStatus(List<Double[]> envelopeList, List<StressIntensityModel> stressList,int fclpSize,int fclpNum) {

        double standardY = envelopeList.get(0)[0];

        double upperY = envelopeList.get(0)[1];

        double lowerY = envelopeList.get(0)[2];

        List<EvaluatConclusion> streeStatusList = new ArrayList();
        EvaluatConclusion evaluatConclusion = new EvaluatConclusion();

        double variance = 0.0;
        for (int i = 0; i < stressList.size(); i++) {
            //应激强度低（中、高）
            double stressIntensity = stressList.get(i).getStressIntensityValue();
            if (stressIntensity > upperY){
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.high.getValue());
            }else if (stressIntensity <= upperY && stressIntensity >= lowerY){
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.middle.getValue());
            }else {
                stressList.get(i).setStressIntensityIndex(Constants.streeStatus.low.getValue());
            }
            //应激稳定度为高（中、低）
            variance = variance + Math.pow((stressList.get(i).getStressIntensityValue() - standardY),2);
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

    /**
     * 计算应激强度上包线值，下包线值，标准包线值
     * @param fclpNum
     * @return
     */
    private List<Double[]> calculationEnvelope(int fclpNum) {

        double e = Math.E;

        //标准曲线
        double standardA = 1604.7881;
        double standardB = -0.025165;
        double standardC = 6311.2841;
        double standardD = -0.00016371;
        double standardY = 0.0;
        standardY =  standardA * Math.pow(e,standardB * fclpNum) + standardC * Math.pow(e,standardD * fclpNum);

        //上限包线
        double upperA = 2172.0256;
        double upperB = -0.045386;
        double upperC = 8628.093;
        double upperD = -0.0003001;
        double upperY = 0.0;
        upperY =  upperA * Math.pow(e,upperB * fclpNum) + upperC * Math.pow(e,upperD * fclpNum);

        //下限包线
        double lowerA = 1159.2422;
        double lowerB = -0.021275;
        double lowerC = 4233.2265;
        double lowerD = -5e-05;
        double lowerY = 0.0;
        lowerY =  lowerA * Math.pow(e,lowerB * fclpNum) + lowerC * Math.pow(e,lowerD * fclpNum);

        List<Double[]> list = new ArrayList<>();
        list.add(new Double[]{standardY,upperY,lowerY});
        return list;
    }

    /**
     * 非FCLP段HRV计算
     * @param fclpList
     * @param rrList
     */
    private List<HRVIndex> calculationHRV(List<Integer[]> fclpList, List<RRData> rrList) {

        List<Integer[]> noFCLPList = new ArrayList<>();
        Integer noFclpBeforeTime = null;
        Integer noFclpAfterTime = null;
        double beforeAvg = 0.0d;
        List<Double> hr = new ArrayList<>();
        //提取心率
        for (int i = 0; i < rrList.size(); i++) {
            hr.add(rrList.get(i).getHr());
        }
        if (fclpList == null || fclpList.size() == 0){//无fclp
            noFclpBeforeTime = rrList.size();
            noFCLPList.add(new Integer[]{0,noFclpBeforeTime});
            double nofclpAvg = CommonUtils.avg(hr);
            double nofclpMax = CommonUtils.calculateMaxValue(hr);
            double nofclpMin = CommonUtils.calculateMinValue(hr);
            Double[] beforeFclpHrData = new Double[]{nofclpAvg,nofclpMax,nofclpMin};
        }else{
            //非fclp段数组,即fclp前和fclp后
            noFCLPList.add(new Integer[]{0,fclpList.get(0)[0] - 1});
            noFCLPList.add(new Integer[]{fclpList.get(fclpList.size()-1)[1]+1,rrList.size()});
            List<Double> hrSubBefore = hr.subList(noFCLPList.get(0)[0],noFCLPList.get(0)[1]);
            beforeAvg = CommonUtils.avg(hrSubBefore);
            //fclp前平均心率、最大心率、最小心率
            double beforeMax = CommonUtils.calculateMaxValue(hrSubBefore);
            double beforeMin = CommonUtils.calculateMinValue(hrSubBefore);
            Double[] beforeFclpHrData = new Double[]{beforeAvg,beforeMax,beforeMin};

            //fclp后平均心率、最大心率、最小心率
            List<Double> hrSubAfter = hr.subList(noFCLPList.get(1)[0],noFCLPList.get(1)[1]);
            double afterAvg = CommonUtils.avg(hrSubAfter);
            double afterMax = CommonUtils.calculateMaxValue(hrSubAfter);
            double afterMin = CommonUtils.calculateMinValue(hrSubAfter);
            Double[] afterFclpHrData = new Double[]{afterAvg,afterMax,afterMin};
        }

        List<Double> RRIntervalDataList = new ArrayList<>();
        for (int i = 0; i < rrList.size(); i++) {//提取RR间期数据
            RRIntervalDataList.add(rrList.get(i).getRRIntervalData());
        }
        List<HRVIndex> hrvList = new ArrayList<>();

        for (int i = 0; i < noFCLPList.size(); i++) {
            //非fclp段HRV计算
            this.calculationHRVCommon(noFCLPList.get(i)[0],noFCLPList.get(i)[1],RRIntervalDataList,hrvList);
        }
        return hrvList;
    }

    /**
     * 非FCLP段HRV指标计算
     * @param start
     * @param end
     * @param RRIntervalDataList
     * @param hrvList
     */
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
        //PNN50 = NN50 / 总NN间期数
        hrvIndex.setPNN50(CommonUtils.division(NN50,(end - start)) * 100);
        //频域指标计算-暂时从给定的范围随机取
        hrvIndex.setHF(CommonUtils.randomDouble(772.0,1178.0));
        hrvIndex.setLF(CommonUtils.randomDouble(754.0,1586.0));
        hrvIndex.setLFNorm(CommonUtils.randomDouble(30.0,78.0));
        hrvIndex.setHFNorm(CommonUtils.randomDouble(26.0,32.0));
        hrvIndex.setLFAndHFRatio(CommonUtils.randomDouble(1.5,2.0));
        hrvList.add(hrvIndex);
    }

    /**
     * FCLP段应激强度计算
     * @param fclpList
     */
    private List<StressIntensityModel> FCLPStressIntensity(List<Integer[]> fclpList,List<RRData> rrList,double calculationIndex) {

        List<StressIntensityModel> stressList = new ArrayList();//各fclp应激强度数组
        for (int i = 0; i < fclpList.size(); i++) {

            int subStartIndex = fclpList.get(i)[0];
            int subEndIndex = fclpList.get(i)[1];
            List<RRData> fclpListSub = rrList.subList(subStartIndex,subEndIndex > rrList.size() ? rrList.size() : subEndIndex);
            //List<RRData> fclpListSubSmooth = CommonUtils.smoothNew(fclpListSub, 0, fclpListSub.size() - 1,(subEndIndex - subStartIndex)/10);
            List<RRData> fclpListSubSmooth = CommonUtils.smoothNew(fclpListSub,0,fclpListSub.size() - 1,100);
            /*double fclpHrAvg = CommonUtils.mean(fclpListSubSmooth);
            for (int j = 0; j < fclpListSubSmooth.size(); j++) {
                fclpListSubSmooth.get(j).setHr(CommonUtils.sub(fclpListSubSmooth.get(j).getHr(),fclpHrAvg));
            }*/

            double smoothListMax = fclpListSubSmooth.stream().max(Comparator.comparing(RRData::getHr)).get().getHr();
            double smoothListMin = fclpListSubSmooth.stream().min(Comparator.comparing(RRData::getHr)).get().getHr();
            double smoothTemp = new BigDecimal((smoothListMax + smoothListMin) / 2).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
            List<Double> smoothListTemp = new ArrayList<>();
            for (int j = 0; j < fclpListSubSmooth.size(); j++) {
                smoothListTemp.add(fclpListSubSmooth.get(j).getHr() - smoothTemp);
            }
            List<Integer> passZeroHrNumList = new ArrayList<>();
            //逐点检查该段心率波形穿越0轴的各点
            /*for (int j = 0; j < smoothListTemp.size() - 1; j++) {
                if (smoothListTemp.get(j) * smoothListTemp.get(j + 1) < 0){
                    passZeroHrNumList.add(j);
                }
            }*/
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
                //两个过0点之间的最大心率的绝对值
                List<RRData> twoZeroHrInterval = fclpListSubSmooth.subList(passZeroHrNumList.get(j),passZeroHrNumList.get(j+1));
                List<Double> hrInterval = new ArrayList<>();
                //提取心率数据
                for (int k = 0; k < twoZeroHrInterval.size(); k++) {
                    hrInterval.add(twoZeroHrInterval.get(k).getHr());
                }
                double maxAbs = Math.abs(CommonUtils.calculateMaxValue(hrInterval));
                //求两个过0点之间的最小心率的绝对值
                double minAbs = Math.abs(CommonUtils.calculateMinValue(hrInterval));
                //如果最大值幅度大于最小值幅度，说明该段心率有波峰
                if (maxAbs > minAbs){
                    StressIntensityModel intensityModel = new StressIntensityModel();
                    //记录此次fclp心率峰值段
                    intensityModel.setStartTime(passZeroHrNumList.get(j) + subStartIndex);
                    intensityModel.setEndTime(passZeroHrNumList.get(j+1) + subStartIndex);
                    double stressIntensityValue = 0.0;//该段应激强度初值
                    //计算应激强度
                    for (int k = passZeroHrNumList.get(j); k < passZeroHrNumList.get(j+1); k++) {
                        //该fclp段当前时刻的心率
                        double hrValueCurrentFclp = fclpListSub.get(k).getHr();
                        double fclpintervalHr1 = rrList.get(fclpList.get(i)[0] + k).getSamplingNum();
                        double fclpintervalHr2 = rrList.get(fclpList.get(i)[0] + k - 1).getSamplingNum();
                        stressIntensityValue += (hrValueCurrentFclp * (fclpintervalHr1 - fclpintervalHr2) * calculationIndex);
                    }
                    intensityModel.setStressIntensityValue(new BigDecimal(stressIntensityValue).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
                    stressList.add(intensityModel);
                }
            }
        }
        return stressList;
    }

    /**
     * FCLP段识别
     * @param rrList
     */
    private List<Integer[]> FCLPIdentity(List<RRData> rrList) {

        int startHrIndex = 0;//起始心心率位置

        List<Integer[]> fclpList = new ArrayList<>();//各fclp段数据

        while (startHrIndex < rrList.size()){

            if ((startHrIndex + segment1) < rrList.size()){

                startHrIndex = this.findFclp(startHrIndex,1,rrList,fclpList);

            }else {//若不够一个判断长度，则按segment0心率长度进行判断，并置序号为心率尾序号,结束判断过程
                if ((rrList.size() - startHrIndex) > segment0){

                    startHrIndex = this.findFclp(startHrIndex,2,rrList,fclpList);

                }else {
                    startHrIndex = rrList.size();
                }
            }
        }
        return fclpList;
    }

    private Integer findFclp(int startHrIndex,int type,List<RRData> rrList,List<Integer[]> fclpList){

        List<RRData> rrListSub = new ArrayList<>();

        List<Integer> passZeroHrNumList = new ArrayList<>();//穿过0心率时刻集合

        if (type == 1){
            rrListSub = rrList.subList(startHrIndex,(startHrIndex + segment1 - 1) > rrList.size() ? rrList.size() : (startHrIndex + segment1 - 1));
        }else {
            rrListSub = rrList.subList(startHrIndex,rrList.size());
        }

        //心率平滑滤波
        List<RRData> smoothList = CommonUtils.smoothNew(rrListSub, 0, rrListSub.size() - 1, 100);
        double smoothListMax = smoothList.stream().max(Comparator.comparing(RRData::getHr)).get().getHr();
        double smoothListMin = smoothList.stream().min(Comparator.comparing(RRData::getHr)).get().getHr();
        double smoothTemp = (smoothListMax + smoothListMin) / 2;
        List<Double> smoothListTemp = new ArrayList<>();
        for (int i = 0; i < smoothList.size(); i++) {
            smoothListTemp.add(new BigDecimal(smoothList.get(i).getHr() - smoothTemp).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
        }
        for (int i = 0; i < smoothListTemp.size() - 1; i++) {
            if (smoothListTemp.get(i) * smoothListTemp.get(i + 1) <= 0){
                passZeroHrNumList.add(i);
            }
        }
        List<Integer> passZeroIntervalNum = new ArrayList<>();//心率过0点的间隔数集合
        for (int i = 0; i < passZeroHrNumList.size() - 1; i++) {
            passZeroIntervalNum.add(rrList.get(startHrIndex+passZeroHrNumList.get(i+1) - 1).getSamplingNum() - rrList.get(startHrIndex+passZeroHrNumList.get(i) - 1).getSamplingNum());
        }
        //寻找fclp段
        if (type == 1){
            if (passZeroHrNumList.size() >= 2 && CommonUtils.calculateMinValueInteger(passZeroIntervalNum) > fclpIndex
                    && (CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) - CommonUtils.calculateMinValueInteger(passZeroIntervalNum)) < fclpIndex1
                    && CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) < fclpIndex2) {
                fclpList.add(new Integer[]{startHrIndex,startHrIndex + segment1 - 1});
                startHrIndex += segment1;//判断指针移动segment1个心率点
            }else {
                startHrIndex += segment0;
            }
        }else {
            if (passZeroHrNumList.size() > 2 && CommonUtils.calculateMinValueInteger(passZeroIntervalNum) > fclpIndex
                    && (CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) - CommonUtils.calculateMinValueInteger(passZeroIntervalNum)) < fclpIndex1
                    && CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) < fclpIndex2){
                fclpList.add(new Integer[]{startHrIndex,startHrIndex + segment0 - 1});
            }
            /*if (passZeroHrNumList.size() > 2 && (CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) - CommonUtils.calculateMinValueInteger(passZeroIntervalNum)) < fclpIndex1
                    && CommonUtils.calculateMaxValueInteger(passZeroIntervalNum) < fclpIndex2){
                fclpList.add(new Integer[]{startHrIndex,startHrIndex + segment0 - 1});
            }*/
            startHrIndex = rrList.size();
        }
        return startHrIndex;
    }

    /**
     * 计算RR间期和心率
     * @param ecgHrDataList
     */
    private List<RRData> calculationRRAndHr(List<EcgHrData> ecgHrDataList) {

        //提取一个腰带的心电数据
        List<Integer> ecgHrTempList = ecgHrDataList.get(0).getEcgList();

        //心电差分波
        List<Double> ecgDifferenceDataList = new ArrayList<>();

        List<Double> ecgList = new ArrayList<>();
        //心电数据类型转换
        ecgList = ecgHrTempList.stream().map(Integer::doubleValue).collect(Collectors.toList());

        List<RRData> RR_HRList = new ArrayList();

        for (int j = 1; j < ecgList.size(); j++) {
            ecgDifferenceDataList.add(CommonUtils.sub(ecgList.get(j),ecgList.get(j - 1)));
        }
        //存储心电差分波数据
        ecgHrDataList.get(0).setEcgDifferenceDataList(ecgDifferenceDataList);

        //寻找可用心电波段&计算RR间期和心率start
        int segmentLength = 300;//心电波判断长度
        int segmentStep = 50;//心电波判断的步进长度
        int ecgStartIndex = 0;//起始心电波位置

        int good_bad_value = 1;//心电数据好坏判据初值

        int lastNumber = 0;//前段最后一个非0心率序号
        while (ecgStartIndex < ecgList.size()){

            if ((ecgStartIndex + segmentLength - 1) < ecgList.size()){

                //取sengment1个点的心电数据
                List<Double> ecgHrDataListSub = ecgList.subList(ecgStartIndex,ecgStartIndex + segmentLength - 1);
                //心电差分&心电减去均值
                List<Double> ecgHrDataListSubDiff = new ArrayList<>();
                double avg = ecgHrDataListSub.stream().mapToDouble(Double::valueOf).average().getAsDouble();//均值
                List<Double> ecgHrSubAvgList = new ArrayList<>();//心电减均值数组
                for (int i = 0; i < ecgHrDataListSub.size() - 1; i++) {
                    double subTemp = ecgHrDataListSub.get(i + 1) - ecgHrDataListSub.get(i);
                    ecgHrDataListSubDiff.add(subTemp > 0.0 ? subTemp : 0.0);
                    double subAvgTemp = ecgHrDataListSub.get(i) - avg;
                    ecgHrSubAvgList.add(subAvgTemp > 0.0 ? subAvgTemp : 0.0);
                }
                //计算确定心电R峰值下限阈值
                double R_peack = this.calculationEcgRAndDiffPeak(ecgHrSubAvgList,segmentLength,segmentStep);
                //计算确定心电差分峰值下限阈值
                double DR_peack = this.calculationEcgRAndDiffPeak(ecgHrDataListSubDiff,segmentLength,segmentStep);
                //心电R峰值&心电差分峰值最大值
                double R_peakMax = ecgHrSubAvgList.stream().max(Comparator.comparing(Double::doubleValue)).get();
                double DR_peakMax = ecgHrDataListSubDiff.stream().max(Comparator.comparing(Double::doubleValue)).get();
                //峰值数组
                if (R_peack == 0.0 || DR_peack == 0.0 || R_peakMax < 20.0 || DR_peakMax < 20.0){
                    R_peack = 0.0;
                    DR_peack = 0.0;
                }
                //如果获得完整的两个峰值下限值，则进行心电数据判读和心率计算
                if (R_peack != 0.0 && DR_peack != 0.0){
                    int minpeakdistance = 15;
                    //找峰值
                    List<PeakModel> ecgHrDataSubPeak = this.calPeakAndPosition(ecgHrDataListSub,R_peack,minpeakdistance);
                    List<PeakModel> differenceEcgPeak = this.calPeakAndPosition(ecgHrDataListSubDiff,DR_peack,minpeakdistance);
                    //峰值数目与差分峰值数目差值
                    int isAvailable = Math.abs(ecgHrDataSubPeak.size() - differenceEcgPeak.size());
                    //差分峰值间隔最大最小差
                    int dissimilar = 0;
                    double d_mean_rr = 0.0;
                    if (ecgHrDataSubPeak.size() < (Math.ceil(segmentLength * 0.02 / 2) + 2) || differenceEcgPeak.size() < (Math.ceil(segmentLength * 0.02 / 2) + 2)){
                        dissimilar = 5;
                    }else {
                        //峰值差分
                        List<Integer> differenceEcgPeakDiff = new ArrayList<>();
                        for (int i = 0; i < differenceEcgPeak.size() - 1; i++) {
                            differenceEcgPeakDiff.add(differenceEcgPeak.get(i+1).getIndex() - differenceEcgPeak.get(i).getIndex());
                        }
                        Integer maxDiff = differenceEcgPeakDiff.stream().max(Comparator.comparing(Integer::intValue)).get();
                        Integer minDiff = differenceEcgPeakDiff.stream().min(Comparator.comparing(Integer::intValue)).get();
                        dissimilar = Math.abs(maxDiff - minDiff);

                        List<Integer> ecgHrDataSubPeakDiff = new ArrayList<>();
                        for (int i = 0; i < ecgHrDataSubPeak.size() - 1; i++) {
                            ecgHrDataSubPeakDiff.add(ecgHrDataSubPeak.get(i+1).getIndex() - ecgHrDataSubPeak.get(i).getIndex());
                        }
                        double diffEcgAvg = ecgHrDataSubPeakDiff.stream().mapToDouble(Integer::doubleValue).summaryStatistics().getAverage();
                        double ecgSubAvg = differenceEcgPeakDiff.stream().mapToDouble(Integer::doubleValue).summaryStatistics().getAverage();
                        d_mean_rr = (ecgSubAvg - diffEcgAvg) / diffEcgAvg;
                        //d_mean_rr = new BigDecimal(d_mean_rr).setScale(1,BigDecimal.ROUND_HALF_UP).doubleValue();
                    }

                    if (isAvailable <  good_bad_value && dissimilar < 5 && d_mean_rr < 0.1){
                        double samplingInterval = 0.02d;

                        for (int i = 0; i < differenceEcgPeak.size() - 1; i++) {
                            RRData rrData = new RRData();
                            rrData.setRRIntervalData((differenceEcgPeak.get(i + 1).getIndex() - differenceEcgPeak.get(i).getIndex()) * samplingInterval);
                            rrData.setHr(60/rrData.getRRIntervalData());
                            rrData.setSamplingNum(ecgStartIndex + differenceEcgPeak.get(i).getIndex());
                            //剔除错误数据
                            if (good_bad_value != 1){
                                if (i == 0){
                                    //前段最后1个心率数据比较
                                    if (Math.abs(rrData.getHr() - RR_HRList.get(lastNumber).getHr()) > 10){
                                        rrData.setRRIntervalData(RR_HRList.get(lastNumber).getRRIntervalData());
                                        rrData.setHr(CommonUtils.mul(CommonUtils.division(1.0,rrData.getRRIntervalData()),60.0));
                                    }
                                }else if (Math.abs(rrData.getHr() - RR_HRList.get(RR_HRList.size()-1).getHr()) > 10){
                                    rrData.setRRIntervalData(RR_HRList.get(RR_HRList.size() - 1).getRRIntervalData());
                                    if (rrData.getRRIntervalData() == 0.0){//防止前刻RR间期为0
                                        rrData.setHr(0.0);
                                    }else {
                                        rrData.setHr(CommonUtils.mul(CommonUtils.division(1.0,rrData.getRRIntervalData()),60.0));
                                    }
                                }
                            }
                            RR_HRList.add(rrData);
                            lastNumber = RR_HRList.size() - 1;
                            good_bad_value = 4;//更改阈值（初始阈值是为了确保第1个心率值正确）
                        }
                        //移动判断指标，继续判断
                        ecgStartIndex += segmentLength;
                    }else {//心电数据差，无法使用，无法计算RR间期和心率HR
                        ecgStartIndex += segmentStep;
                        RRData rrData = new RRData();
                        rrData.setRRIntervalData(0.0);
                        rrData.setHr(0.0);
                        rrData.setSamplingNum(ecgStartIndex-1);
                        RR_HRList.add(rrData);
                    }
                }else {//如果不能获得两个完整的峰值下限判断值，则该段心电数据无法使用
                    ecgStartIndex += segmentStep;
                    RRData rrData = new RRData();
                    rrData.setRRIntervalData(0.0);
                    rrData.setHr(0.0);
                    rrData.setSamplingNum(ecgStartIndex-1);
                    RR_HRList.add(rrData);
                }
            }else {
                ecgStartIndex = ecgList.size();
            }
        }
        //0心率处理
        List<Integer> indList = new ArrayList<>();//非0心率位置集合
        for (int i = 0; i < RR_HRList.size(); i++) {
            if (RR_HRList.get(i).getHr() != 0.0){
                indList.add(i);
            }
        }
        if (indList.size() > 0){
            for (int i = indList.get(0); i < RR_HRList.size(); i++) {
                if (RR_HRList.get(i).getHr() == 0.0 && i != 0){
                    RR_HRList.get(i).setRRIntervalData(RR_HRList.get(i-1).getRRIntervalData());
                    RR_HRList.get(i).setHr(RR_HRList.get(i-1).getHr());
                }
            }
            if (indList.get(0) != 0){
                RR_HRList = RR_HRList.subList(indList.get(0),RR_HRList.size());
            }
        }
        return RR_HRList;
    }

    private double calculationEcgRAndDiffPeak(List<Double> ecgHrSubAvgList,int segmentLength,int segmentStep) {
        List<PeakModel> peakModelList = CommonUtils.findPeakList(ecgHrSubAvgList);//找出心电的所有峰值和峰值位置
        //按心电峰值排序
        List<PeakModel> peakModelListSort = peakModelList.stream().sorted(Comparator.comparing(PeakModel::getPeakValue))
                .collect(Collectors.toList());
        //排序后的心电做差分处理
        List<PeakModel> peakModelListSortDiff = new ArrayList<>();
        for (int i = 0; i < peakModelListSort.size() - 1; i++) {
            PeakModel peakModel = new PeakModel();
            peakModel.setPeakValue(new BigDecimal(peakModelListSort.get(i+1).getPeakValue() - peakModelListSort.get(i).getPeakValue())
                    .setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
            peakModel.setIndex(i);
            peakModelListSortDiff.add(peakModel);
        }
        //心电峰值排序差分后的最大值位置
        PeakModel peakModel = new PeakModel();
        if (peakModelListSortDiff.size() > Math.ceil(segmentLength * 0.02 / 2)){
            peakModel = peakModelListSortDiff.stream().max(Comparator.comparing(PeakModel::getPeakValue)).get();
            //处理差分假峰值
            if (peakModel.getIndex() + 1 > peakModelListSortDiff.size() - 1){
                peakModelListSortDiff.remove(peakModelListSortDiff.size() - 1);
                peakModel = peakModelListSortDiff.stream().max(Comparator.comparing(PeakModel::getPeakValue)).get();
            }
        }
        //心电峰值最低限值
        double R_peack = new BigDecimal(peakModelListSort.get(peakModel.getIndex()+1).getPeakValue() - 1.0).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        return R_peack;
    }

    /**
     * 提取心电波形数据
     * @param contentList
     */
    private List<EcgHrData> getEcgWaveData(List<Integer> contentList,File file) {

        List<Integer> stposList = new ArrayList();
        //截取(85,170)之间的数据
        for (int i = 0; i < contentList.size()-1; i++) {
            if ((int)contentList.get(i) == headByte1 && (int)contentList.get(i+1) == headByte2){
                stposList.add(i);
                i += 22;
            }
        }
        if (stposList.size() == 0){
            logger.info("未找到数据帧头");
        }else {
            int pFirst = (int) stposList.get(0);//首帧位置
            int pLast = (int) stposList.get(stposList.size()-1);//末帧位置
        }
        List<List<Integer>> pDataList = new ArrayList<>();
        int lPacket = 23;
        for (int i = 0; i < stposList.size()-1; i++) {//stposList.size()-1:最后一帧可能不完整，舍去
            int endIndex = (stposList.get(i) + lPacket) < contentList.size() ? (stposList.get(i) +lPacket) : contentList.size();
            pDataList.add(contentList.subList(stposList.get(i),endIndex));
        }
        List ecgList = new ArrayList();
        int ecgHighIndex = 256;
        for (int i = 0; i < pDataList.size(); i++) {
            ecgList.add(pDataList.get(i).get(3) * ecgHighIndex + pDataList.get(i).get(4));
        }
        List<EcgHrData> ecgHrDataList = new ArrayList<>();
        EcgHrData ecgHrData = new EcgHrData();
        ecgHrData.setEcgFileName(file.getName());
        ecgHrData.setEcgDataTime(new Date());//暂设为当前日期，后续变更为从源数据文件读取
        ecgHrData.setEcgList(ecgList);
        ecgHrDataList.add(ecgHrData);

        return ecgHrDataList;
    }

    /**
     * 计算波峰及位置
     * @param ecgList
     * @param minpeakheight
     * @param minpeakdistance
     * @return
     */
    private List<PeakModel> calPeakAndPosition(List<Double> ecgList,double minpeakheight,int minpeakdistance){

        if (CollectionUtil.isEmpty(ecgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            int flag = 0;
            List<PeakModel> peakList = new ArrayList<>();
            for (int i = 0; i < ecgList.size() - 1;) {//只记录波峰及位置
                if (ecgList.get(i + 1).compareTo(ecgList.get(i)) > 0){
                    flag = 2;
                }else if (ecgList.get(i + 1).compareTo(ecgList.get(i)) < 0){
                    if (flag == 2){
                        if (ecgList.get(i).compareTo(minpeakheight) >= 0){
                            PeakModel peakModel = new PeakModel();
                            peakModel.setIndex(i);
                            peakModel.setPeakValue(ecgList.get(i));
                            peakList.add(peakModel);
                            i += minpeakdistance;
                            continue;
                        }
                    }
                    flag = 1;
                }
                i++;
            }
            return peakList;
        }
    }
    /*private List<Double[]> calPeakAndPosition(List<Double> ecgList, double max, double ratio) {
        if (CollectionUtil.isEmpty(ecgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            //峰值最小高度
            double multiplyValue1 = new BigDecimal(max * ratio).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
            int flag = 0;
            List<Double[]> peakList = new ArrayList<>();
            for (int i = 1; i < ecgList.size(); i++) {//只记录波峰及位置
                if (ecgList.get(i).compareTo(ecgList.get(i-1)) > 0){
                    flag = 2;
                }else if (ecgList.get(i).compareTo(ecgList.get(i-1)) < 0){
                    if (flag == 2){
                        if (ecgList.get(i-1).compareTo(multiplyValue1) >= 0){//以峰值高度为最大值的0.7倍为基准，寻找峰值及其位置
                            peakList.add(new Double[]{Double.valueOf(i-1),ecgList.get(i-1)});
                        }
                    }
                    flag = 1;
                }
            }
            return peakList;
        }
    }*/

}
