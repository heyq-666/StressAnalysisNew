package com.zxkkj.stressAnalysis;

import com.zxkkj.stressAnalysis.utils.CommonUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ApplicationNew {
    public static void main(String[] args) {
        ECGDataProcessor ecgDataProcessor = new ECGDataProcessor();
        BreathRateCalculator breathRateCalculator = new BreathRateCalculator();

        try {
            //读取心率数据
            ecgDataProcessor.processData("/Users/heyuqi/Desktop/stress/测试数据new/BeltData2023-4-10_15-45-41_王斌彪.dat");
            ecgDataProcessor.filterECGData();
            //呼吸率计算
            breathRateCalculator.calculateBreathRate(ecgDataProcessor.getBreathData());
            //心率计算
            RRIntervalCalculator rrIntervalCalculator = new RRIntervalCalculator(ecgDataProcessor.getEcgMeanFilter());
            rrIntervalCalculator.calculateRRIntervals();
            //FCLP检测

            //用之前的方法
            List<Double> hrData = rrIntervalCalculator.getHeartRate().stream().map(v -> v.getHeartRate()).collect(Collectors.toList());
            FCLPDetector detector = new FCLPDetector(hrData);
            detector.detectFCLPSegments();

            List<Integer[]> fclpNo = CommonUtils.fclpIdentity(rrIntervalCalculator.getHeartRate());

            // 获取FCLP段数据
            List<FCLPDetector.FCLPSegment> fclpSegments = detector.getFCLPSegments();

            // 输出FCLP段数据
            System.out.println("FCLP段数据:");
            fclpSegments.forEach(System.out::println);

            // 可视化FCLP段
            detector.visualizeFCLPSegments(rrIntervalCalculator.getHeartRate());
            //绘制
            //1.呼吸波
            BreathDataPanel breathDataPanel = new BreathDataPanel(ecgDataProcessor.getBreathData());
            breathDataPanel.plotBreathData();
            //2.心电波
            ECGDataPanel ecgDataPanel = new ECGDataPanel(ecgDataProcessor.getEcgData());
            ecgDataPanel.plotECGData();
            //3.加速度
            AccelerationDataPanel accelerationDataPanel = new AccelerationDataPanel(ecgDataProcessor.getYzxData());
            accelerationDataPanel.plotAccelerationData();
            //4.呼吸率
            BreathRatePanel breathRatePanel = new BreathRatePanel(breathRateCalculator.getBreathRate());
            breathRatePanel.plotBreathRate();
            //5.心率
            /*HeartRatePanel heartRatePanel = new HeartRatePanel(rrIntervalCalculator.getHeartRate(),rrIntervalCalculator.getRRIntervals());
            heartRatePanel.plotHeartRate();*/
            rrIntervalCalculator.visualizeHeartRate();
        } catch (IOException e) {
            System.err.println("处理数据时出错: " + e.getMessage());
        }
    }
}
