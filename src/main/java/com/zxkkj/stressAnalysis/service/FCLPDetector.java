package com.zxkkj.stressAnalysis.service;

import com.zxkkj.stressAnalysis.model.RRData;

import java.util.ArrayList;
import java.util.List;

public class FCLPDetector {

    private static final int SEGMENT1 = 2000; // 主段落长度
    private static final int SEGMENT0 = 100;  // 递进步长

    public List<Integer[]> detectFCLPSegments(List<RRData> hrList) {
        // 提取心率值列表
        List<Double> hrValues = new ArrayList<>(hrList.size());
        for (RRData data : hrList) {
            hrValues.add(data.getHr());
        }

        List<Integer[]> fclpList = new ArrayList<>();
        int i = 0; // 当前处理位置

        while (i < hrValues.size()) {
            if (i + SEGMENT1 <= hrValues.size()) {
                // 处理完整段落
                List<Double> segment = hrValues.subList(i, i + SEGMENT1);
                if (isFCLPSegment(segment)) {
                    fclpList.add(new Integer[]{i, i + SEGMENT1 - 1});
                    i += SEGMENT1; // 跳过整个段落
                } else {
                    i += SEGMENT0; // 递进步长
                }
            } else {
                // 处理剩余部分
                int remaining = hrValues.size() - i;
                if (remaining > SEGMENT0) {
                    List<Double> segment = hrValues.subList(i, i + SEGMENT0);
                    if (isFCLPSegment(segment)) {
                        fclpList.add(new Integer[]{i, i + SEGMENT0 - 1});
                    }
                }
                i = hrValues.size(); // 结束处理
            }
        }

        return fclpList;
    }

    private boolean isFCLPSegment(List<Double> segment) {
        // 1. 平滑处理并减去均值
        List<Double> smoothed = smooth(segment, 100);
        double mean = segment.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        List<Double> normalized = new ArrayList<>(smoothed.size());
        for (double value : smoothed) {
            normalized.add(value - mean);
        }

        // 2. 检测过零点
        List<Integer> zeroCrossings = new ArrayList<>();
        for (int k = 0; k < normalized.size() - 1; k++) {
            double current = normalized.get(k);
            double next = normalized.get(k + 1);

            if (Math.signum(current) != Math.signum(next)) {
                zeroCrossings.add(k);
            }
        }

        // 3. 检查过零点数量
        if (zeroCrossings.size() <= 2) {
            return false;
        }

        // 4. 计算过零点间隔
        List<Integer> intervals = new ArrayList<>();
        for (int j = 0; j < zeroCrossings.size() - 1; j++) {
            intervals.add(zeroCrossings.get(j + 1) - zeroCrossings.get(j));
        }

        // 5. 分析间隔特征
        int minInterval = intervals.stream().min(Integer::compare).orElse(0);
        int maxInterval = intervals.stream().max(Integer::compare).orElse(0);

        return minInterval > 105 && (maxInterval - minInterval) < 200;
    }

    private List<Double> smooth(List<Double> data, int windowSize) {
        List<Double> smoothed = new ArrayList<>(data.size());
        int halfWindow = windowSize / 2;

        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(data.size(), i + halfWindow + 1);

            double sum = 0;
            int count = 0;
            for (int j = start; j < end; j++) {
                sum += data.get(j);
                count++;
            }
            smoothed.add(sum / count);
        }
        return smoothed;
    }
}