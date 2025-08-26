package com.zxkkj.stressAnalysis.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvWriter;
import com.zxkkj.stressAnalysis.calculator.*;
import com.zxkkj.stressAnalysis.model.AnalysisReult;
import com.zxkkj.stressAnalysis.model.EvaluatConclusion;
import com.zxkkj.stressAnalysis.model.StressPoint;
import com.zxkkj.stressAnalysis.panel.BreathRatePanel;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.utils.ArgsParser;
import com.zxkkj.stressAnalysis.utils.OutputConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AutomaticAnalysisServiceImpl implements IAnalysisService {
    private static Logger logger = LoggerFactory.getLogger(AutomaticAnalysisServiceImpl.class);
    @Override
    public void analyze(ArgsParser parser) {
        List<File> files = paramVerify(parser.getArgs());
        if (files != null && !files.isEmpty()) {
            startAnalysis(files, parser.getArgs()[2]);
        } else {
            logger.warn("未找到有效文件");
        }
    }
    private static List<File> paramVerify(String[] args) {
        String folderPath = args[1];
        if (!FileUtil.exist(folderPath)) {
            logger.error("filePath: %s is not exist", folderPath);
            return null;
        }
        List<File> fileList = FileUtil.loopFiles(folderPath);
        return fileList;
    }

    public void startAnalysis(List<File> files, String outTxtPath) {
        for (File file : files){
            String fileName = file.getName();
            String filePath = file.getAbsolutePath();
            logger.info("file:{} begin analysis:{}", fileName);
            try {
                //计算结果存储
                AnalysisReult analysisResult = new AnalysisReult();

                ECGDataProcessor ecgDataProcessor = new ECGDataProcessor();
                //读取心率数据
                ecgDataProcessor.processData(filePath);
                ecgDataProcessor.filterECGData();

                //呼吸率计算
                BreathRateCalculator breathRateCalculator = new BreathRateCalculator();
                breathRateCalculator.calculateBreathRate(ecgDataProcessor.getBreathData());

                //心率计算
                RRIntervalCalculator rrIntervalCalculator = new RRIntervalCalculator(ecgDataProcessor.getEcgMeanFilter());
                rrIntervalCalculator.calculateRRIntervals();

                //FCLP检测
                List<Double> hrData = rrIntervalCalculator.getHeartRate().stream().map(v -> v.getHeartRate()).collect(Collectors.toList());
                FCLPDetector detector = new FCLPDetector(hrData);
                detector.detectFCLPSegments();
                List<FCLPDetector.FCLPSegment> fclpSegments = detector.getFCLPSegments();

                // 计算应激强度
                StressCalculator calculator = new StressCalculator(fclpSegments, rrIntervalCalculator.getHeartRate());
                calculator.calculateStress();
                List<StressPoint> stressPoints = calculator.getStressPoints();

                // 应激强度高中低判断
                StressValueCalculator valueCalculator = new StressValueCalculator(fclpSegments,stressPoints);
                valueCalculator.calculateStressValue();
                List<EvaluatConclusion> evaluatConclusions = valueCalculator.getEvaluatConclusions();

                // 非fclp段HRV计算
                NoFCLPHrvCalculator noFCLPHrvCalculator = new NoFCLPHrvCalculator(fclpSegments,stressPoints,rrIntervalCalculator.getRRIntervals());
                noFCLPHrvCalculator.noFCLPHrvCalculator();
                // 保存结果
                analysisResult.setFclpIsExit(CollectionUtils.isEmpty(fclpSegments) ? 0 : 1);
                analysisResult.setHrvList(noFCLPHrvCalculator.getHrvList());
                analysisResult.setEvaluatConclusionList(evaluatConclusions);
                analysisResult.setEcgList(ecgDataProcessor.getEcgData());
                analysisResult.setHrList(rrIntervalCalculator.getHeartRate().stream().map(v -> v.getHeartRate()).collect(Collectors.toList()));
                analysisResult.setBreathData(ecgDataProcessor.getBreathData());
                analysisResult.setBreathRate(breathRateCalculator.getBreathRate());
                analysisResult.setYzxData(ecgDataProcessor.getYzxData());
                analysisResult.setFclpSegments(fclpSegments);
                analysisResult.setFclpNum(CollectionUtils.isEmpty(fclpSegments) ? 0 : fclpSegments.size());
                analysisResult.setStressPoints(stressPoints);
                analysisResult.setRRList(rrIntervalCalculator.getRRIntervals().stream().map(v -> v.getRrInterval()).collect(Collectors.toList()));
                //输出
                this.outAnalysisResult(analysisResult,outTxtPath,file);
                logger.info("file: {} analysis success", fileName);

                /*BreathRatePanel panel = new BreathRatePanel(breathRateCalculator.getBreathRate());
                panel.plotBreathRate();*/

            }catch (Exception e) {
                logger.error("file: {} analysis failure - 原因: {}" ,fileName, e.getMessage());
            }
        }
    }

    private void outAnalysisResult(AnalysisReult analysisReult, String outTxtPath, File file) {

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
                for (int i = 0; i < analysisReult.getStressPoints().size(); i++) {
                    fw.write(analysisReult.getStressPoints().get(i).getStressValue() + "" + " ");
                    fw.write(analysisReult.getStressPoints().get(i).getStressIntensityIndex() + "" + " ");
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
            //将腰带源数据提取的心电/心率/呼吸/加速度数据输出至指定位置
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

    private void outECGAndHrListToCsv(AnalysisReult analysisResult, String outTxtPath) {
        // 定义输出配置列表
        List<OutputConfig> outputConfigs = Arrays.asList(
                new OutputConfig("ecg.csv", () -> analysisResult.getEcgList()),
                new OutputConfig("hr.csv", () -> analysisResult.getHrList()),
                new OutputConfig("RR.csv", () -> analysisResult.getRRList()),
                new OutputConfig("breathData.csv", () -> analysisResult.getBreathData()),
                new OutputConfig("breathRate.csv", () -> analysisResult.getBreathRate()),
                new OutputConfig("yzxData.csv", () -> analysisResult.getYzxData())
        );

        // 统一处理所有输出
        for (OutputConfig config : outputConfigs) {
            CsvWriter writer = null;
            FileOutputStream fos = null;
            OutputStreamWriter osw = null;

            try {
                String filePath = outTxtPath + config.fileName;
                fos = new FileOutputStream(filePath);
                // 写入UTF-8 BOM头
                fos.write(0xEF);
                fos.write(0xBB);
                fos.write(0xBF);

                osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                writer = new CsvWriter(osw);

                // 获取并写入数据
                Object data = config.dataSupplier.get();
                if (data instanceof List) {
                    writer.write((List<?>) data);
                }
            } catch (Exception e) {
                // 记录错误并继续处理其他文件
                logger.info("Error writing to " + config.fileName + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 关闭资源
                try {
                    if (writer != null) writer.close();
                } catch (Exception e) {
                    logger.info("Error closing writer for " + config.fileName);
                }
                try {
                    if (osw != null) osw.close();
                } catch (Exception e) {
                    logger.info("Error closing OutputStreamWriter for " + config.fileName);
                }
                try {
                    if (fos != null) fos.close();
                } catch (Exception e) {
                    logger.info("Error closing FileOutputStream for " + config.fileName);
                }
            }
        }
    }
}
