package com.zxkkj.stressAnalysis.service;

import com.zxkkj.stressAnalysis.model.AnalysisReult;
import com.zxkkj.stressAnalysis.model.EcgHrData;
import java.io.File;
import java.io.IOException;

/**
 * 腰带监测分析服务
 */
public interface IAnalysisService {

    /**
     * 读取数据
     * @param fileName
     * @param filePath
     * @return
     * @throws IOException
     */
    EcgHrData loadDataByLocalFile(String fileName, String filePath);

    /**
     * 基于整条腰带的数据进行数据分析
     * @param content
     * @return
     */
    AnalysisReult executeAnalysis(EcgHrData content, File file);

    /**
     * 基于手动选取的fclp心率数据进行数据分析
     * @param fclpNum
     * @param fclpStage
     * @param hrArray
     * @param outTxtPath
     * @return
     */
    AnalysisReult executeAnalysisManual(String fclpNum, String fclpStage, String hrArray,String outTxtPath);

    /**
     * 基于整条腰带的数据进行结果输出
     * @param analysisReult
     */
    void outAnalysisResult(AnalysisReult analysisReult,String outTxtPath,File file);

    /**
     * 基于手动选取的fclp心率数据进行结果输出
     * @param analysisReult
     */
    void outAnalysisResultManual(AnalysisReult analysisReult,String outTxtPath);

}
