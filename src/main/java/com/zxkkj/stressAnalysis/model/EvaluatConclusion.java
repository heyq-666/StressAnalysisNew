package com.zxkkj.stressAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author javabage
 * @date 2022/9/13
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluatConclusion {

    /**
     * 应激适应度
     */
    private int stressFitness;
    /**
     * 应激稳定度
     */
    private int stressStability;
}
