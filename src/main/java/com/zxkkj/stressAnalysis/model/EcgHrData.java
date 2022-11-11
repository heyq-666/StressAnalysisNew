package com.zxkkj.stressAnalysis.model;

import lombok.*;

import java.util.Date;
import java.util.List;

/**
 * @author javabage
 * @date 2022/8/1
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EcgHrData {

    /**
     * 心电波形数据文件名
     */
    private String ecgFileName;

    /**
     * 心电波形数据文件日期
     */
    private Date ecgDataTime;

    /**
     * 心电波形数据
     */
    private List<Integer> ecgList;

    /**
     * 心电差分波
     */
    private List<Double> ecgDifferenceDataList;

}
