package com.zxkkj.stressAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BreathRateCalculator {
    private final List<BreathRatePoint> breathRate = new ArrayList<>();

    // 参数配置
    private static final int SEGMENT1 = 300; // 一次呼吸波计算长度（采样点数）
    private static final int SEGMENT0 = 25;  // 呼吸波判断的步进长度（1秒）
    private static final int BREATH_L = 8;   // 呼吸率合理范围下限
    private static final int BREATH_H = 40;  // 呼吸率合理范围上限
    private static final double SAMPLE_RATE = 0.04; // 采样率（秒/点）

    /**
     * 计算呼吸率
     */
    public void calculateBreathRate(List<Double> breathData) {
        int i = 0; // 呼吸波数据序号
        int breathNum = 0; // 呼吸率数据序号

        while (i + SEGMENT1 - 1 < breathData.size()) {
            // 提取一段呼吸波形
            List<Double> segment = breathData.subList(i, i + SEGMENT1);

            // 对呼吸波进行平滑滤波
            List<Double> smoothed = smoothData(segment, 100);

            // 对呼吸波进行平移
            double mean = smoothed.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            List<Double> normalized = smoothed.stream()
                    .map(val -> val - mean)
                    .collect(Collectors.toList());

            // 寻找过零点的时间间隔
            List<Integer> zeroCrossings = findZeroCrossings(normalized);

            if (zeroCrossings.size() >= 2) {
                // 计算时间间隔的中值
                double medianValue = calculateMedianInterval(zeroCrossings);

                // 计算各时间间隔与中值差的绝对值
                List<Double> diffValues = zeroCrossings.stream()
                        .map(interval -> Math.abs(interval - medianValue))
                        .collect(Collectors.toList());

                // 找到与中值差别最小的两个元素
                List<Double> sortedDiffs = diffValues.stream()
                        .sorted()
                        .collect(Collectors.toList());

                double element1 = sortedDiffs.get(0);
                double element2 = sortedDiffs.get(1);

                // 如果时间间隔差别足够小
                if ((element1 + element2) / medianValue < 0.2) {
                    // 计算呼吸率
                    double breathRateValue = 60.0 / (medianValue * SAMPLE_RATE * 2);

                    // 如果呼吸率在合理范围内，则计算存储
                    if (breathRateValue >= BREATH_L && breathRateValue < BREATH_H) {
                        breathRate.add(new BreathRatePoint(breathRateValue, i + SEGMENT1));
                        breathNum++;
                        i += SEGMENT1; // 向后移动12秒，继续处理
                        System.out.println("处理位置: " + i);
                    } else {
                        // 否则不计算，后移后继续
                        i += SEGMENT0;
                        System.out.println("%呼吸波超过合理范围，处理位置: " + i);
                    }
                } else {
                    i += SEGMENT0;
                    System.out.println("%时间间隔差别太大，呼吸波不正确，不能计算此刻的呼吸率，处理位置: " + i);
                }
            } else {
                i += SEGMENT0;
                System.out.println("%时间间隔数目少于2个，呼吸波不正确，不能计算此刻的呼吸率，处理位置: " + i);
            }
        }

        System.out.println("呼吸率计算完成，共计算 " + breathRate.size() + " 个呼吸率点");
    }

    /**
     * 对数据进行平滑滤波（移动平均）
     */
    private List<Double> smoothData(List<Double> data, int windowSize) {
        List<Double> smoothed = new ArrayList<>();
        int halfWindow = windowSize / 2;

        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(data.size(), i + halfWindow + 1);

            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += data.get(j);
            }

            smoothed.add(sum / (end - start));
        }

        return smoothed;
    }

    /**
     * 寻找过零点的时间间隔
     */
    private List<Integer> findZeroCrossings(List<Double> data) {
        List<Integer> zeroCrossings = new ArrayList<>();

        // 找到第一个非零值的位置和符号
        int firstNonZeroIndex = IntStream.range(0, data.size())
                .filter(idx -> Math.abs(data.get(idx)) > 1e-10)
                .findFirst()
                .orElse(-1);

        if (firstNonZeroIndex == -1) {
            return zeroCrossings; // 没有非零值
        }

        int sign = (int) Math.signum(data.get(firstNonZeroIndex));
        int lastCrossing = firstNonZeroIndex;

        // 遍历数据寻找过零点
        for (int i = firstNonZeroIndex + 1; i < data.size(); i++) {
            double value = data.get(i);

            if (Math.abs(value) > 1e-10) { // 忽略接近零的值
                int currentSign = (int) Math.signum(value);

                if (currentSign != sign) {
                    // 找到过零点，记录时间间隔
                    zeroCrossings.add(i - lastCrossing);
                    lastCrossing = i;
                    sign = currentSign;
                }
            }
        }

        return zeroCrossings;
    }

    /**
     * 计算时间间隔的中值
     */
    private double calculateMedianInterval(List<Integer> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            throw new IllegalArgumentException("Input list cannot be null or empty");
        }
        List<Integer> sorted = intervals.stream()
                .sorted()
                .collect(Collectors.toList());

        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * 获取计算出的呼吸率数据
     */
    public List<BreathRatePoint> getBreathRate() {
        return breathRate;
    }

    /**
     * 呼吸率数据点类
     */
    public static class BreathRatePoint {
        private final double rate;
        private final int position;

        public BreathRatePoint(double rate, int position) {
            this.rate = rate;
            this.position = position;
        }

        public double getRate() {
            return rate;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return "BreathRatePoint{" +
                    "rate=" + rate +
                    ", position=" + position +
                    '}';
        }
    }
}