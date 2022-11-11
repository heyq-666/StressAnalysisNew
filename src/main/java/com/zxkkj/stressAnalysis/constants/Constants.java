package com.zxkkj.stressAnalysis.constants;

import lombok.Getter;

/**
 * @author javabage
 * @date 2022/8/1
 */

public class Constants {

    public enum streeStatus {

        low(0), //应激强度低、应激不稳阶段、应激稳定度差
        middle(1), //应激强度中、应激渐稳阶段、应激稳定度良好
        high(2) ;//应激强度高、应激稳定阶段、应激稳定度好

        @Getter
        private int value;

        streeStatus(int value) {
            this.value= value;
        }
    }

    public enum fclpType{

        automatic("1"),//自动计算
        manual("2"),//手动选取

        before("1"),//fclp前
        inter("2"),//fclp间
        after("3");//fclp后

        @Getter
        private String value;

        fclpType(String value){
            this.value = value;
        }
    }

}
