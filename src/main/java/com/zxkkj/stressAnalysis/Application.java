package com.zxkkj.stressAnalysis;

import com.zxkkj.stressAnalysis.service.AnalysisServiceFactory;
import com.zxkkj.stressAnalysis.service.IAnalysisService;
import com.zxkkj.stressAnalysis.utils.ArgsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

public class Application {

    private static Logger logger = LoggerFactory.getLogger(Application.class);
    public static void main(String[] args) {
        try {
            logger.info("程序启动，入参: {}", Arrays.toString(args));

            // 参数预处理-调试用
            //String[] processedArgs = simulationParam(1);

            ArgsParser parser = new ArgsParser(args);

            Optional<IAnalysisService> serviceOptional = AnalysisServiceFactory.getService(parser.getType());

            if (serviceOptional.isPresent()) {
                serviceOptional.get().analyze(parser);
            } else {
                throw new IllegalArgumentException("不支持的参数类型");
            }
        } catch (Exception e) {
            logger.error("程序执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("parameter transfer error");
        }
    }

    private static String[] simulationParam(int type) {
        if (type == 1){
            //自动计算
           return new String[]{
                    "1",
                    "/Users/heyuqi/Desktop/stress/测试数据new/BeltData2023-4-10_15-45-41_王斌彪.dat",
                    "/Users/heyuqi/Desktop/stress/stressOut/"};
        }else if (type == 2){
            //手动选取
            //args[0]:1：代表自动计算、2：代表手动选取
            //args[1]:FCLP序号
            //args[2]:FCLP阶段（1：代表FCLP前、2：代表FCLP间期、3：代表FCLP后）
            String[] temp = new String[2500];
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 2500; i++) {
                temp[i] = new BigDecimal(Math.random() * (120.0 - 70.0) + 70.0).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                stringBuilder.append(temp[i] + ",");
            }
            return new String[]{"2","1","2",stringBuilder.toString(),"/Users/heyuqi/Desktop/stress/stressOut/"};
        }
        return null;
    }
}
