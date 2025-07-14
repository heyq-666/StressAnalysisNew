package com.zxkkj.stressAnalysis.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.text.csv.CsvWriter;
import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.model.*;
import com.zxkkj.stressAnalysis.service.FCLPDetector;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.utils.CommonUtils;
import com.zxkkj.stressAnalysis.utils.ExcelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AnalysisServiceImpl implements IAnalysisService {

    private Logger logger = LoggerFactory.getLogger(getClass());
    //头标志字节
    private final int headByte1 = 85;
    private final int headByte2 = 170;
    //判断fclp所用指标
    private static final byte[] FRAME_HEADER = {85, (byte) 170}; // 0x55, 0xAA
    private static final byte[] FRAME_TRAILER = {(byte) 170, 85}; // 0xAA, 0x55
    private static final int FRAME_LENGTH = 279;
    private static final int ECG_PER_FRAME = 100;
    private static final int HEADER_LENGTH = 2;
    private static final int SEQ_OFFSET = 2;
    private static final int SEQ_LENGTH = 4;
    private static final int ECG_OFFSET = 13;

    // 正常ECG判定的时间范围（根据采样率调整）
    private static final int LENGTH_NORMAL = 500;
    // 脉冲检测阈值
    private static final double PULSE_THRESHOLD = 200;

    @Override
    public EcgHrData loadDataByLocalFile(String fileName,String filePath) {
        // 使用内存映射文件提高大文件读取性能
        /*try (FileChannel channel = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ)) {
            long fileSize = channel.size();
            logger.info("======fileSize:{}",fileSize);
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

            // 查找第一个帧头位置
            int firstHeaderPos = findFirstHeader(buffer);
            if (firstHeaderPos == -1) {
                throw new RuntimeException("未找到数据帧头");
            }

            return parseECGData(fileName,buffer,firstHeaderPos);
        }catch (IOException e){
            logger.info("======loadDataByLocalFile error:{}",e.getMessage());
        }
        return null;*/
        try (FileInputStream fis = new FileInputStream(filePath)){
            BufferedInputStream bis = new BufferedInputStream(fis);
            // 读取整个文件到字节数组
            byte[] fileBytes = readAllBytes(bis);
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            // 查找第一个帧头位置
            int firstHeaderPos = findFirstHeader(buffer);
            if (firstHeaderPos == -1) {
                throw new RuntimeException("未找到数据帧头");
            }
            return parseECGData(fileName,buffer,firstHeaderPos);
        }catch (IOException e){
            logger.info("======loadDataByLocalFile error:{}",e.getMessage());
        }
        return null;
    }

    private static byte[] readAllBytes(BufferedInputStream bis) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = bis.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

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

    private static EcgHrData parseECGData(String fileName,ByteBuffer buffer, int startPos) {
        List<Integer> frameNumbers = new ArrayList<>();
        List<Double> ecgData = new ArrayList<>();

        int position = startPos;
        int bufferLimit = buffer.limit();
        int frameCount = 0;

        // 预计算结束位置
        int maxPosition = bufferLimit - FRAME_LENGTH;

        while (position <= maxPosition) {
            // 检查帧头
            if (buffer.get(position) == FRAME_HEADER[0] &&
                    buffer.get(position + 1) == FRAME_HEADER[1]) {

                // 检查帧尾
                int trailerPos = position + FRAME_LENGTH - 2;
                if (trailerPos + 1 < bufferLimit &&
                        buffer.get(trailerPos) == FRAME_TRAILER[0] &&
                        buffer.get(trailerPos + 1) == FRAME_TRAILER[1]) {

                    // 解析帧序号 (大端序)
                    int frameNum = 0;
                    for (int i = 0; i < SEQ_LENGTH; i++) {
                        frameNum = (frameNum << 8) | (buffer.get(position + SEQ_OFFSET + i) & 0xFF);
                    }
                    frameNumbers.add(frameNum);

                    // 批量解析ECG数据
                    int ecgStart = position + ECG_OFFSET;
                    for (int i = 0; i < ECG_PER_FRAME; i++) {
                        int pos = ecgStart + i * 2;
                        double ecgValue = ((buffer.get(pos) & 0xFF) << 8) | (buffer.get(pos + 1) & 0xFF);
                        ecgData.add(ecgValue);
                    }

                    // 移动到下一帧
                    position += FRAME_LENGTH;
                    frameCount++;
                    continue; // 跳过位置递增
                }
            }
            position++; // 未找到有效帧，前进1字节
        }

        return new EcgHrData(fileName, LocalDateTime.now(),frameNumbers, ecgData,null);
    }

    private static int findFirstHeader(ByteBuffer buffer) {
        int limit = buffer.limit() - FRAME_HEADER.length;
        for (int i = 0; i <= limit; i++) {
            if (buffer.get(i) == FRAME_HEADER[0] && buffer.get(i + 1) == FRAME_HEADER[1]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public AnalysisReult executeAnalysis(EcgHrData content,File file) {

        //结果输出
        AnalysisReult analysisReult = new AnalysisReult();

        //提取心电波形数据
        EcgHrData ecgHrData = this.processECGSignal(file,content);

        //存储心电波
        analysisReult.setEcgList(ecgHrData.getEcgList());

        //计算RR间期和心率
        List<RRData> RRList = this.calculationRRAndHr(ecgHrData);
        List<Double> RRDataList = RRList.stream().map(RRData::getRRIntervalData).collect(Collectors.toList());
        analysisReult.setRRList(RRDataList);

        //提取每秒的心率数据
        List<Double> hrList = this.getHrList(RRList);
        analysisReult.setHrList(hrList);

        //FCLP段识别
        List<Integer[]> fclpList = this.fclpIdentity(RRList);
        /*FCLPDetector fclpDetector = new FCLPDetector();
        List<Integer[]> fclpList1 = fclpDetector.detectFCLPSegments(RRList);*/
        analysisReult.setFclpList(fclpList);

        //FCLP段应激强度计算
        List<StressIntensityModel> stressList = this.FCLPStressIntensityNew(analysisReult,fclpList,RRList);

        //应激强度高中低判断
        this.judgeStressIntensity(analysisReult,stressList);

        //非fclp段HRV计算
        this.calculationNoFclpHrv(fclpList,stressList,RRList,analysisReult);

        return analysisReult;
    }

    public EcgHrData processECGSignal(File file,EcgHrData parseResult) {
        // 获取原始ECG数据
        List<Double> ecgList = parseResult.getEcgList();

        // 1. 均值滤波
        double[] meanFilteredArray = computeMeanFilter(ecgList);

        // 2. 脉冲干扰检测与处理
        double[] processedArray = processPulseInterference(meanFilteredArray);

        // 3. 信号方向调整
        adjustSignalDirection(processedArray);

        List<Double> processedList = new ArrayList<>(processedArray.length);
        for (double value : processedArray) {
            processedList.add(value);
        }
        //输出心电波数据-用于调试
        //outEcgData(file,processedList);
        return new EcgHrData(parseResult.getEcgFileName(),parseResult.getEcgDataTime(),parseResult.getFrameNumbers(),processedList,null);
    }

    private static void outEcgData(File file,List<Double> processedList) {
        ExcelWriter.writeDoublesToExcelColumn(processedList, "/Users/heyuqi/Desktop/stress/stressOut/"+file.getName() + ".xlsx");
    }

    // 内部实现使用数组 - 调整信号方向
    private static void adjustSignalDirection(double[] signal) {
        double maxPositive = Double.NEGATIVE_INFINITY;
        double minNegative = Double.POSITIVE_INFINITY;

        // 查找最大值和最小值
        for (double value : signal) {
            if (value > maxPositive) maxPositive = value;
            if (value < minNegative) minNegative = value;
        }

        // 如果最大正值小于最小负值的绝对值，则反转信号
        if (maxPositive < Math.abs(minNegative)) {
            for (int i = 0; i < signal.length; i++) {
                signal[i] = -signal[i];
            }
        }
    }

    private double[] processPulseInterference(double[] ecgSignal) {
        // 创建副本，避免修改原始数据
        double[] processed = Arrays.copyOf(ecgSignal, ecgSignal.length);

        // 初始计算最大值和正常ECG判定值
        double ecgMax = CommonUtils.findMaxAbsolute(processed);
        int maxIndex = CommonUtils.findFirstMaxIndex(processed, ecgMax);
        double normalECG = this.calculateNormalECG(processed, maxIndex);

        // 检查是否需要处理脉冲干扰
        if (ecgMax < normalECG || ecgMax <= PULSE_THRESHOLD) {
            System.out.println("无脉冲干扰");
            return processed;
        }

        // 脉冲干扰检测与剔除循环
        while (ecgMax >= 2 * normalECG || ecgMax > PULSE_THRESHOLD) {
            // 剔除脉冲干扰点（置零）
            for (int i = 0; i < processed.length; i++) {
                if (Math.abs(processed[i]) >= ecgMax) {
                    processed[i] = 0;
                }
            }

            // 重新计算均值并平移
            double newMean = CommonUtils.computeArrayMean(processed);
            for (int i = 0; i < processed.length; i++) {
                processed[i] -= newMean;
            }

            // 更新最大值和正常ECG判定值
            ecgMax = CommonUtils.findMaxAbsolute(processed);
            maxIndex = CommonUtils.findFirstMaxIndex(processed, ecgMax);
            normalECG = calculateNormalECG(processed, maxIndex);
        }

        return processed;
    }

    /**
     * 计算正常ECG判定值
     * @param signal
     * @param maxIndex
     * @return
     */
    private static double calculateNormalECG(double[] signal, int maxIndex) {
        double normalECG;

        if (maxIndex + LENGTH_NORMAL < signal.length) {
            // 取后续时间范围内的局部最大值
            normalECG = CommonUtils.findMaxInRange(signal, maxIndex + 1, maxIndex + LENGTH_NORMAL);
        } else if (maxIndex - LENGTH_NORMAL >= 0) {
            // 取前面时间范围内的局部最大值
            normalECG = CommonUtils.findMaxInRange(signal, maxIndex - LENGTH_NORMAL, maxIndex - 1);
        } else {
            // 没有足够数据，使用整个信号的最大值
            normalECG = CommonUtils.findMaxAbsolute(signal);
        }
        return normalECG;
    }

    private static double[] computeMeanFilter(List<Double> ecgList) {
        if (ecgList == null || ecgList.isEmpty()) {
            return new double[0];
        }
        // 转换为数组提高性能
        double[] ecgArray = ecgList.stream().mapToDouble(i -> i).toArray();
        // 计算总和
        long sum = 0;
        for (double value : ecgArray) {
            sum += value;
        }
        // 计算平均值
        final double mean = (double) sum / ecgArray.length;

        // 减去平均值
        double[] filtered = new double[ecgArray.length];
        for (int i = 0; i < ecgArray.length; i++) {
            filtered[i] = ecgArray[i] - mean;
        }

        return filtered;
    }

    /**
     * 应激强度高中低判断
     * @param analysisReult
     * @param stressList
     */
    private void judgeStressIntensity(AnalysisReult analysisReult,List<StressIntensityModel> stressList) {
        if (analysisReult.getFclpIsExit() == 1){
            //先计算判断应激强度高中低的上线包线
            List<Double[]> EnvelopeList = CommonUtils.calculationEnvelope(Integer.valueOf(1));
            //根据应激强度高中低的上线包线来计算应激强度高、中、低
            List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,stressList,stressList.size(),1);
            analysisReult.setEvaluatConclusionList(streeStatus);
        }
    }

    /**
     * 非FCLP段HRV指标计算
     * @param stressList
     * @param RRList
     * @param analysisReult
     */
    private void calculationNoFclpHrv(List<Integer[]> fclpList,List<StressIntensityModel> stressList, List<RRData> RRList, AnalysisReult analysisReult) {

        List<Integer[]> fclpNo = new ArrayList<>();

        /*fclpNo.add(new Integer[]{1,fclpList.get(0)[0]});
        fclpNo.add(new Integer[]{fclpList.get(fclpList.size()-1)[1],RRList.size()-1});*/
        if (fclpList.size() == 0 || fclpList == null){
            fclpNo.add(new Integer[]{0,RRList.size() - 1});
            //fclp前的心率均值、最大值、最小值
            List<RRData> listBeforeFclp = RRList.subList(fclpNo.get(0)[0],fclpNo.get(0)[1]);
            double fclpBeforeMean = CommonUtils.mean(listBeforeFclp);
            double fclpBeforeMax = listBeforeFclp.stream().mapToDouble(RRData::getHr).max().getAsDouble();
            double fclpBeforeMin = listBeforeFclp.stream().mapToDouble(RRData::getHr).min().getAsDouble();
            Double[] fclpBeforeData = new Double[]{fclpBeforeMean,fclpBeforeMax,fclpBeforeMin};
            analysisReult.setFclpBeforeData(fclpBeforeData);
        }else {
            fclpNo.add(new Integer[]{0,stressList.get(0).getStartTime() - 1});
            fclpNo.add(new Integer[]{stressList.get(stressList.size()-1).getEndTime() + 1,RRList.size() - 1});
            //fclp前的心率均值、最大值、最小值
            List<RRData> listBeforeFclp = RRList.subList(fclpNo.get(0)[0],fclpNo.get(0)[1]);
            double fclpBeforeMean = CommonUtils.mean(listBeforeFclp);
            double fclpBeforeMax = listBeforeFclp.stream().mapToDouble(RRData::getHr).max().getAsDouble();
            double fclpBeforeMin = listBeforeFclp.stream().mapToDouble(RRData::getHr).min().getAsDouble();
            Double[] fclpBeforeData = new Double[]{fclpBeforeMean,fclpBeforeMax,fclpBeforeMin};

            //fclp后的心率均值、最大值、最小值
            List<RRData> listAfterFclp = RRList.subList(fclpNo.get(1)[0],fclpNo.get(1)[1]);
            double fclpAfterMean = CommonUtils.mean(listAfterFclp);
            double fclpAfterMax = listAfterFclp.stream().mapToDouble(RRData::getHr).max().getAsDouble();
            double fclpAfterMin = listAfterFclp.stream().mapToDouble(RRData::getHr).min().getAsDouble();
            Double[] fclpAfterData = new Double[]{fclpAfterMean,fclpAfterMax,fclpAfterMin};

            analysisReult.setFclpBeforeData(fclpBeforeData);
            analysisReult.setFclpAfterData(fclpAfterData);
        }
        List<HRVIndex> hrvList = new ArrayList<>();
        //提取RR间期数组
        List<Double> RRIntervalDataList = RRList.stream().map(RRData::getRRIntervalData).collect(Collectors.toList());
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
                SDNN += Math.pow(RRList.get(j).getRRIntervalData() - meanRR,2);
                if (j < fclpNo.get(i)[1]){
                    RMSSD += Math.pow(RRList.get(j + 1).getRRIntervalData()- RRList.get(j).getRRIntervalData(),2);
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
            hrvList.add(hrvIndex);
        }
        analysisReult.setHrvList(hrvList);
    }

    /**
     * FCLP应激强度计算
     * @param fclpList
     * @param RRList
     * @return
     */
    private List<StressIntensityModel> FCLPStressIntensityNew(AnalysisReult analysisReult,List<Integer[]> fclpList, List<RRData> RRList) {

        List<StressIntensityModel> stressList = new ArrayList<>();

        if (CollectionUtil.isEmpty(fclpList) || CollectionUtil.isEmpty(RRList)){

            StressIntensityModel intensityModel = new StressIntensityModel();
            intensityModel.setStartTime(0);
            intensityModel.setEndTime(0);
            intensityModel.setStressIntensityIndex(0);
            intensityModel.setStressIntensityValue(0);
            stressList.add(intensityModel);
            analysisReult.setStressIntensityModelList(stressList);
            analysisReult.setFclpNum(0);
            analysisReult.setFclpIsExit(0);
            return stressList;
        }else {

            for (int i = 0; i < fclpList.size(); i++) {
                List<RRData> fclpHr = RRList.subList(fclpList.get(i)[0],fclpList.get(i)[1]);
                //平滑窗口
                int window = (int) Math.ceil((fclpList.get(i)[1] - fclpList.get(i)[0]) / 10);
                List<RRData> fclpHrSmooth = CommonUtils.smoothNew(fclpHr,0,fclpHr.size()-1,window);
                double meanFclpHr = CommonUtils.mean(fclpHrSmooth);
                List<Double> fclpHrSmoothNew = fclpHrSmooth.stream().map(item -> {
                    double hr = CommonUtils.keepTwoDecimal(item.getHr() - meanFclpHr);
                    return hr;
                }).collect(Collectors.toList());
                //检查该段心率波形穿越0轴的各点
                List<Integer> passZeroList = new ArrayList<>();
                for (int j = 0; j < fclpHrSmoothNew.size() - 1; j++) {
                    if (fclpHrSmoothNew.get(j + 1) * fclpHrSmoothNew.get(j) < 0){
                        passZeroList.add(j);
                    }
                }
                //处理各过0点之间的数据
                for (int j = 0; j < passZeroList.size() - 1; j++) {
                    double max = CommonUtils.calculateMaxValue(fclpHrSmoothNew.subList(passZeroList.get(j),passZeroList.get(j+1)));
                    double maxAbs = Math.abs(max);
                    double min = CommonUtils.calculateMinValue(fclpHrSmoothNew.subList(passZeroList.get(j),passZeroList.get(j+1)));
                    double minAbs = Math.abs(min);
                    //如果最大值幅度大于最小值幅度，说明该段心率有波峰
                    if (maxAbs > minAbs){
                        StressIntensityModel intensityModel = new StressIntensityModel();
                        intensityModel.setStartTime(passZeroList.get(j));
                        intensityModel.setEndTime(passZeroList.get(j+1));
                        //在两个过0点之间计算应激强度
                        for (int k = passZeroList.get(j); k < passZeroList.get(j + 1); k++) {
                            //应激强度计算因子
                            double CalculationFactor1 = intensityModel.getStressIntensityValue();
                            double CalculationFactor2 = fclpHr.get(k).getHr();
                            double CalculationFactor3 = RRList.get(fclpList.get(i)[0] + k).getSamplingNum() - RRList.get(fclpList.get(i)[0] + k - 1).getSamplingNum();
                            double CalculationFactor4 = 0.02d;
                            double stressIntensityValue = new BigDecimal(CalculationFactor1 + CalculationFactor2 * CalculationFactor3 * CalculationFactor4)
                                    .setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                            intensityModel.setStressIntensityValue(stressIntensityValue);
                        }
                        stressList.add(intensityModel);
                    }
                }
            }
            analysisReult.setStressIntensityModelList(stressList);
            analysisReult.setFclpNum(stressList.size());
            analysisReult.setFclpIsExit(1);
            return stressList;
        }
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
                if (stressList.size() > 0){//如果手动选取的fclp识别不出来应激动作，那视为无fclp，只计算hrv
                    stressIntensityValue = new BigDecimal((stressIntensityValue / stressList.size())).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
                    StressIntensityModel intensityModel = new StressIntensityModel();
                    intensityModel.setStartTime(stressList.get(0).getStartTime());
                    intensityModel.setEndTime(stressList.get(stressList.size() - 1).getEndTime());
                    intensityModel.setStressIntensityValue(stressIntensityValue);
                    stressListNew.add(intensityModel);

                    //计算每个fclp的应激强度低（中、高），应激适应度阶段，应激稳定度
                    List<Double[]> EnvelopeList = CommonUtils.calculationEnvelope(Integer.valueOf(fclpNum));
                    List<EvaluatConclusion> streeStatus = this.calculatioStreeStatus(EnvelopeList,stressListNew,1,Integer.valueOf(fclpNum));
                    //结果记录
                    analysisReult.setFclpIsExit(1);
                    analysisReult.setFclpNum(1);//手动选取单次fclp，故输出的fclp次数始终为1
                    analysisReult.setStressIntensityModelList(stressListNew);
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
    /**
     * 计算每秒心率集合
     * @param rrList
     * @return
     */
    private List<Double> getHrList(List<RRData> rrList) {
        // 复制list并进行时间取整
        List<RRData> rrListNew = rrList.stream().map(e -> {
                    RRData newData = new RRData(e.getRRIntervalData(), e.getHr(), e.getSamplingNum());
                    newData.setSamplingNum((int)(e.getSamplingNum() * 0.02));
                    return newData;
                }).collect(Collectors.toList());

        // 根据取整后的时间去重
        List<RRData> newList = rrListNew.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(RRData::getSamplingNum))),
                        ArrayList::new));

        // 缺失时间段的心率插值(前后平均值)
        List<RRData> endList = new ArrayList<>();
        for (int i = 0; i < newList.size() - 1; i++) {
            RRData current = newList.get(i);
            RRData next = newList.get(i + 1);
            int timeDiff = next.getSamplingNum() - current.getSamplingNum();
            // 添加当前数据点
            endList.add(new RRData(current.getRRIntervalData(), current.getHr(), current.getSamplingNum()));
            // 插值处理
            if (timeDiff > 1) {
                double avgHr = (current.getHr() + next.getHr()) / 2;
                for (int j = 1; j < timeDiff; j++) {
                    endList.add(new RRData(0.0, avgHr, current.getSamplingNum() + j));
                }
            }
        }
        // 添加最后一个数据点
        if (!newList.isEmpty()) {
            RRData last = newList.get(newList.size() - 1);
            endList.add(new RRData(last.getRRIntervalData(), last.getHr(), last.getSamplingNum()));
        }

        // 输出心率数组，保留两位小数
        return endList.stream().map(RRData::getHr).map(CommonUtils::keepTwoDecimal).collect(Collectors.toList());

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
                for (int i = 0; i < 11; i++) {
                    fw.write("- ");
                }
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
            List<Double> ecgList = analysisReult.getEcgList();
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
     * FCLP间期应激强度计算-手动选点
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
        //开始计算应激强度
        List<StressIntensityModel> stressIntensityModelList = new ArrayList<>();
        for (int i = 0; i < fclpList.size(); i++) {

            int subStartIndex = fclpList.get(i)[0];
            int subEndIndex = fclpList.get(i)[1];
            List<RRData> fclpListSub = list.subList(subStartIndex,subEndIndex > rrList.size() ? rrList.size() : subEndIndex);
            List<RRData> fclpListSubSmooth = CommonUtils.smoothNew(fclpListSub,0,fclpListSub.size() - 1,100);
            double smoothListMax = fclpListSubSmooth.stream().max(Comparator.comparing(RRData::getHr)).get().getHr();
            double smoothListMin = fclpListSubSmooth.stream().min(Comparator.comparing(RRData::getHr)).get().getHr();
            double smoothTemp = CommonUtils.keepTwoDecimal((smoothListMax + smoothListMin) / 2);

            List<Double> smoothListTemp = fclpListSubSmooth.stream().map(item -> {
                double hr = item.getHr() - smoothTemp;
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
                List<RRData> twoZeroHrInterval = fclpListSubSmooth.subList(passZeroHrNumList.get(j),passZeroHrNumList.get(j+1));
                //提取心率数据
                List<Double> hrInterval = twoZeroHrInterval.stream().map(RRData::getHr).collect(Collectors.toList());
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
                        double fclpintervalHr1 = list.get(fclpList.get(i)[0] + k).getSamplingNum();
                        double fclpintervalHr2 = list.get(fclpList.get(i)[0] + k - 1).getSamplingNum();
                        stressIntensityValue += (hrValueCurrentFclp * (fclpintervalHr1 - fclpintervalHr2) * 0.02d);
                    }
                    intensityModel.setStressIntensityValue(CommonUtils.keepTwoDecimal(stressIntensityValue));
                    stressIntensityModelList.add(intensityModel);
                }
            }
        }
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
     * FCLP段识别
     * @param hrList
     */
    private List<Integer[]> fclpIdentity(List<RRData> hrList) {

        //各fclp段数据
        List<Integer[]> fclpList = new ArrayList<>();
        int startHrIndex = 1;
        int segmentNew1=2000;
        int segmentNew0=100;
        while (startHrIndex < hrList.size()){
            if (startHrIndex + segmentNew1 - 1 < hrList.size()){
                List<RRData> hrListSub = hrList.subList(startHrIndex,(startHrIndex + segmentNew1));
                List<RRData> hrListSmooth = CommonUtils.smoothNew(hrListSub,0,hrListSub.size() - 1,100);
                double means = CommonUtils.mean(hrListSmooth);
                List<Double> hrListEnd = hrListSmooth.stream().map(item -> {
                    double hr = CommonUtils.keepTwoDecimal(item.getHr() - means);
                    return hr;
                }).collect(Collectors.toList());
                //获取心率过0点位置
                List<Integer> passZeroHrNumList = new ArrayList<>();
                for (int i = 0; i < hrListEnd.size() - 1; i++) {
                    if (hrListEnd.get(i) * hrListEnd.get(i+1) < 0){
                        passZeroHrNumList.add(i);
                    }
                }
                //过0点心率的间隔
                List<Integer> passZeroIntervalNum = new ArrayList<>();
                for (int i = 0; i < passZeroHrNumList.size() - 1; i++) {
                    passZeroIntervalNum.add(passZeroHrNumList.get(i + 1) - passZeroHrNumList.get(i));
                }
                int passZeroMin = CommonUtils.calculateMinValueInteger(passZeroIntervalNum);
                int passZeroMax = CommonUtils.calculateMaxValueInteger(passZeroIntervalNum);
                int sub = passZeroMax - passZeroMin;
                if (passZeroHrNumList.size() > 2 && passZeroMin > 105 && sub < 200){
                    fclpList.add(new Integer[]{startHrIndex,startHrIndex + segmentNew1 - 1});
                    startHrIndex += segmentNew1;
                }else {
                    startHrIndex += segmentNew0;
                }
            }else {//若不够一个判断长度，则按segment0心率长度进行判断
                if (hrList.size() - startHrIndex > segmentNew0){
                    List<RRData> hrListSub = hrList.subList(startHrIndex,(startHrIndex + segmentNew0 - 1));
                    List<RRData> hrListSmooth = CommonUtils.smoothNew(hrListSub,0,hrListSub.size() - 1,100);
                    double means = CommonUtils.mean(hrListSmooth);
                    List<Double> hrListEnd = hrListSmooth.stream().map(item -> {
                        double hr = CommonUtils.keepTwoDecimal(item.getHr() - means);
                        return hr;
                    }).collect(Collectors.toList());
                    //获取心率过0点位置
                    List<Integer> passZeroHrNumList = new ArrayList<>();
                    for (int i = 0; i < hrListEnd.size() - 1; i++) {
                        if (hrListEnd.get(i) * hrListEnd.get(i+1) < 0){
                            passZeroHrNumList.add(i);
                        }
                    }
                    //过0点心率的间隔
                    List<Integer> passZeroIntervalNum = new ArrayList<>();
                    for (int i = 0; i < passZeroHrNumList.size() - 1; i++) {
                        passZeroIntervalNum.add(passZeroHrNumList.get(i + 1) - passZeroHrNumList.get(i));
                    }
                    int passZeroMin = CommonUtils.calculateMinValueInteger(passZeroIntervalNum);
                    int passZeroMax = CommonUtils.calculateMaxValueInteger(passZeroIntervalNum);
                    int sub = passZeroMax - passZeroMin;
                    if (passZeroHrNumList.size() > 2 && passZeroMin > 105 && sub < 200){
                        fclpList.add(new Integer[]{startHrIndex,startHrIndex + segmentNew0 - 1});
                    }
                    startHrIndex = hrList.size();
                }else {
                    startHrIndex = hrList.size();
                }
            }
        }
        return fclpList;
    }

    /**
     * 计算RR间期和心率
     * @param ecgHrData
     */
    private List<RRData> calculationRRAndHr(EcgHrData ecgHrData) {

        //提取一个腰带的心电数据
        List<Double> ecgHrTempList = ecgHrData.getEcgList();

        //心电差分波
        List<Double> ecgDifferenceDataList = new ArrayList<>();

        List<Double> ecgList = ecgHrTempList;

        List<RRData> RR_HRList = new ArrayList();

        for (int j = 1; j < ecgList.size(); j++) {
            ecgDifferenceDataList.add(CommonUtils.sub(ecgList.get(j),ecgList.get(j - 1)));
        }
        //存储心电差分波数据
        ecgHrData.setEcgDifferenceDataList(ecgDifferenceDataList);

        //寻找可用心电波段&计算RR间期和心率start
        int segmentLength = 500;//心电波判断长度
        int segmentStep = 100;//心电波判断的步进长度
        int ecgStartIndex = 0;//起始心电波位置

        int good_bad_value = 1;//心电数据好坏判据初值

        int lastNumber = 0;//前段最后一个非0心率序号
        while (ecgStartIndex < ecgList.size()) {

            if ((ecgStartIndex + segmentLength - 1) < ecgList.size()) {

                //取sengment1个点的心电数据
                List<Double> ecgHrDataListSub = ecgList.subList(ecgStartIndex, ecgStartIndex + segmentLength - 1);
                //心电差分数组
                List<Double> ecgHrDataListSubDiff = new ArrayList<>();
                for (int i = 0; i < ecgHrDataListSub.size() - 1; i++) {
                    ecgHrDataListSubDiff.add(ecgHrDataListSub.get(i + 1) - ecgHrDataListSub.get(i));
                }
                //心电最大值
                double ecgMax = CommonUtils.calculateMaxValue(ecgHrDataListSub);
                //心电差分最大值
                double ecgDiffMax = CommonUtils.calculateMaxValue(ecgHrDataListSubDiff);
                //心电峰值
                List<PeakModel> peakModelEcg = CommonUtils.findPeakListNew(ecgHrDataListSub, ecgMax * 0.6);
                //心电差分峰值
                List<PeakModel> peakModelEcgDiff = CommonUtils.findPeakListNew(ecgHrDataListSubDiff, ecgDiffMax * 0.38);
                int godOrBad = Math.abs(peakModelEcg.size() - peakModelEcgDiff.size());
                if (godOrBad < good_bad_value) {
                    for (int i = 0; i < peakModelEcgDiff.size() - 1; i++) {
                        RRData rrData = new RRData();
                        rrData.setRRIntervalData((peakModelEcgDiff.get(i + 1).getIndex() - peakModelEcgDiff.get(i).getIndex()) * 0.01);
                        rrData.setHr(60 / rrData.getRRIntervalData());
                        rrData.setSamplingNum(ecgStartIndex + peakModelEcgDiff.get(i).getIndex() - 1);
                        //剔除错误数据
                        if (good_bad_value != 1) {
                            if (i == 0) {
                                //前段最后1个心率数据比较
                                if (Math.abs(rrData.getHr() - RR_HRList.get(lastNumber).getHr()) > 10) {
                                    rrData.setRRIntervalData(RR_HRList.get(lastNumber).getRRIntervalData());
                                    rrData.setHr(CommonUtils.mul(CommonUtils.division(1.0, rrData.getRRIntervalData()), 60.0));
                                }
                            } else {
                                if (Math.abs(rrData.getHr() - RR_HRList.get(RR_HRList.size() - 1).getHr()) > 10) {
                                    rrData.setRRIntervalData(RR_HRList.get(RR_HRList.size() - 1).getRRIntervalData());
                                    rrData.setHr(CommonUtils.mul(CommonUtils.division(1.0, rrData.getRRIntervalData()), 60.0));
                                }
                            }
                        }
                        RR_HRList.add(rrData);
                        lastNumber = RR_HRList.size() - 1;
                        good_bad_value = 3;//更改阈值（初始阈值是为了确保第1个心率值正确）
                    }
                    //移动判断指标，继续判断
                    ecgStartIndex += segmentLength;
                } else {
                    ecgStartIndex += segmentStep;
                    RRData rrData = new RRData();
                    rrData.setRRIntervalData(0.0);
                    rrData.setHr(0.0);
                    rrData.setSamplingNum(ecgStartIndex - 1);
                    RR_HRList.add(rrData);
                }
            }else {
                ecgStartIndex = ecgList.size();
            }
        }
        //修正高心率插补0心率
        return this.correctHighHeartAndZeroHeart(RR_HRList);
        /*List<Integer> indList = new ArrayList<>();//非0心率位置集合
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
        return RR_HRList;*/
    }

    private List<RRData> correctHighHeartAndZeroHeart(List<RRData> rrHrList) {
        // 1. 查找所有非零心率的位置索引
        List<Integer> nonZeroIndices = IntStream.range(0, rrHrList.size())
                .filter(i -> rrHrList.get(i).getHr() != 0)
                .boxed()
                .collect(Collectors.toList());

        if (nonZeroIndices.isEmpty()) {
            logger.error("采集信号无效，无有效心率");
            return Collections.emptyList();
        }

        // 2. 处理第一个心率值大于200的情况
        if (rrHrList.get(0).getHr() > 200) {
            rrHrList.get(0).setHr(200);
            rrHrList.get(0).setRRIntervalData((1.0 / 200) * 60);
        }

        // 3. 处理其他心率值大于200的情况
        IntStream.range(1, rrHrList.size())
                .filter(i -> rrHrList.get(i).getHr() > 200)
                .forEach(i -> {
                    rrHrList.get(i).setHr(rrHrList.get(i-1).getHr());
                    rrHrList.get(i).setRRIntervalData(rrHrList.get(i-1).getRRIntervalData());
                });

        // 4. 创建心率数据副本
        List<RRData> hrList = rrHrList.stream()
                .map(data -> new RRData(data.getRRIntervalData(), data.getHr(), data.getSamplingNum()))
                .collect(Collectors.toList());

        // 5. 处理零心率值(用前一个值替换)
        int firstNonZeroIndex = nonZeroIndices.get(0);
        for (int i = firstNonZeroIndex; i < hrList.size(); i++) {
            if (hrList.get(i).getHr() == 0) {
                hrList.get(i).setHr(hrList.get(i-1).getHr());
                hrList.get(i).setRRIntervalData(hrList.get(i-1).getRRIntervalData());
            }
        }

        // 6. 如果第一个非零心率不是第一个数据，则删除前面的数据
        if (firstNonZeroIndex != 0) {
            hrList = hrList.subList(firstNonZeroIndex, hrList.size());
        }

        return hrList;
    }

    /**
     * 计算心电峰值最低限值-（暂时保留）
     * @param ecgHrSubAvgList
     * @param segmentLength
     * @param segmentStep
     * @return
     */
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
        double R_peack = new BigDecimal(peakModelListSort.get(peakModel.getIndex() + 1).getPeakValue() - 1.0).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
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
        //ecgHrData.setEcgDataTime(new Date());//暂设为当前日期，后续变更为从源数据文件读取
        ecgHrData.setEcgList(ecgList);
        ecgHrDataList.add(ecgHrData);

        return ecgHrDataList;
    }

}