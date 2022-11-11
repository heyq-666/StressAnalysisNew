package com.zxkkj.stressAnalysis;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.model.AnalysisReult;
import com.zxkkj.stressAnalysis.model.ExecuteResult;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.service.impl.AnalysisServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

/**
 * 应用程序
 * @author javabage
 * @date 2022/7/27
 */
public class Application {

    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws ParseException {

        try {
            //模拟入参方法，测试用
            int type = 1;
            args = simulationParam(args,type);

            ExecuteResult executeResult = new ExecuteResult();

            if (args[0].equals(Constants.fclpType.automatic.getValue())){

                executeResult = startAnalysis(args[1],args[2]);

            }else if (args[0].equals(Constants.fclpType.manual.getValue())){

                executeResult = startAnalysisManual(args[1],args[2],args[3],args[4]);

            }else {
                throw new RuntimeException("parameter transfer error");
            }

            logger.info("analysis finished: %s", JSONObject.toJSONString(executeResult));

        } catch (Exception e) {
            logger.error("startAnalysis error,", e);
        }

    }

    private static String[] simulationParam(String[] args,int type) {

        if (type == 1){
            //自动计算
            args = new String[3];
            args[0] = "1";
            args[1] = "C:\\Users\\Heyq\\Desktop\\streeTest\\test3new";
            args[2] = "C:\\Users\\Heyq\\Desktop\\streeTest\\";
        }else if (type == 2){
            //手动选取
            args = new String[5];
            args[0] = "2";//（1：代表自动计算、2：代表手动选取）
            args[1] = "1";//FCLP序号
            args[2] = "2";//FCLP阶段（1：代表FCLP前、2：代表FCLP间期、3：代表FCLP后）
            args[4] = "C:\\Users\\Heyq\\Desktop\\streeTest\\";
            String[] ss = new String[2500];
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 2500; i++) {
                ss[i] = new BigDecimal(Math.random() * (120.0 - 70.0) + 70.0).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                stringBuilder.append(ss[i] + ",");
            }
            String args3 = String.valueOf(stringBuilder);
            args[3] = args3;
        }
        return args;
    }

    /**
     * 数据分析-基于整个腰带数据进行分析
     * @param fileDir
     * @return
     * @throws IOException
     */
    public static ExecuteResult startAnalysis(String fileDir,String outTxtPath) throws IOException {

        if (!FileUtil.exist(fileDir)) {
            logger.error("filePath: %s is not exist", fileDir);
            return new ExecuteResult();
        }
        //执行结果: 成功多少 失败多少
        ExecuteResult executeResult = new ExecuteResult();
        List<File> fileList = FileUtil.loopFiles(fileDir);

        for (File file : fileList){

            logger.info("file: %s begin analysis", file.getName());

            IAnalysisService analysisService = new AnalysisServiceImpl();

            List<Integer> content = analysisService.loadDataByLoaclFile(file);

            AnalysisReult analysisResult = analysisService.executeAnalysis(content,file);

            analysisService.outAnalysisResult(analysisResult,outTxtPath,file);

        }
        return executeResult;
    }

    /**
     * 基于手动选取的FCLP前、间、后进行数据分析
     * @param fclpNum
     * @param fclpStage
     * @param hrArray
     * @param outTxtPath
     * @return
     * @throws IOException
     */
    public static ExecuteResult startAnalysisManual(String fclpNum,String fclpStage,String hrArray,String outTxtPath) throws IOException {

        //执行结果: 成功多少 失败多少
        ExecuteResult executeResult = new ExecuteResult();

        IAnalysisService analysisService = new AnalysisServiceImpl();

        AnalysisReult analysisResult = analysisService.executeAnalysisManual(fclpNum,fclpStage,hrArray,outTxtPath);

        analysisService.outAnalysisResultManual(analysisResult,outTxtPath);

        return executeResult;
    }
}
