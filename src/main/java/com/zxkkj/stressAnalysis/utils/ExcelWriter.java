package com.zxkkj.stressAnalysis.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;

import java.util.List;
import java.util.stream.Collectors;

public class ExcelWriter {
    public static class DoubleData {
        @ExcelProperty("数值列")
        private Double value;

        public DoubleData(Double value) {
            this.value = value;
        }

        // getter 和 setter
        public Double getValue() {
            return value;
        }

        public void setValue(Double value) {
            this.value = value;
        }
    }

    public static void writeDoublesToExcelColumn(List<Double> data, String filePath) {
        // 转换数据格式
        List<DoubleData> excelData = data.stream()
                .map(DoubleData::new).collect(Collectors.toList());
        // 写入Excel
        EasyExcel.write(filePath, DoubleData.class)
                .sheet("Data")
                .doWrite(excelData);
    }
}
