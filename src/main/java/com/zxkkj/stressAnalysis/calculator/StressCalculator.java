package com.zxkkj.stressAnalysis.calculator;

import com.zxkkj.stressAnalysis.model.RRIntervalPoint;
import com.zxkkj.stressAnalysis.model.StressPoint;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StressCalculator {
    private final List<FCLPDetector.FCLPSegment> fclpSegments;
    private final List<RRIntervalPoint> heartRate;
    private final List<StressPoint> stressPoints = new ArrayList<>();

    public StressCalculator(List<FCLPDetector.FCLPSegment> fclpSegments,
                            List<RRIntervalPoint> heartRate) {
        this.fclpSegments = fclpSegments;
        this.heartRate = heartRate;
    }

    /**
     * 计算FCLP段应激强度
     */
    public void calculateStress() {
        int numm = 0; // FCLP序号初值

        for (FCLPDetector.FCLPSegment segment : fclpSegments) {
            // 截取该段FCLP心率数据
            List<Double> s = extractHeartRateValues(segment);

            // 对心率数据进行平滑滤波
            int windowSize = (int) Math.ceil((segment.getEndIndex() - segment.getStartIndex()) / 10.0);
            List<Double> smoothed = smoothData(s, windowSize);

            // 去均值处理
            double mean = smoothed.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            List<Double> normalized = smoothed.stream()
                    .map(val -> val - mean)
                    .collect(Collectors.toList());

            // 寻找过零点
            List<Integer> zeroCrossings = findZeroCrossings(normalized);

            // 处理各过0点之间的数据
            for (int k = 0; k < zeroCrossings.size() - 1; k++) {
                int startIdx = zeroCrossings.get(k);
                int endIdx = zeroCrossings.get(k + 1);

                // 获取该段数据
                List<Double> segmentData = normalized.subList(startIdx, endIdx + 1);

                // 计算最大值和最小值
                double maxVal = segmentData.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double minVal = segmentData.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

                // 如果最大值幅度大于最小值幅度，说明该段心率有波峰（不是波谷）
                if (Math.abs(maxVal) > Math.abs(minVal)) {
                    double stressValue = 0.0;

                    // 在两个过0点之间计算应激强度
                    for (int j = startIdx; j <= endIdx; j++) {
                        int hrIndex = segment.getStartIndex() + j;
                        if (hrIndex < heartRate.size() && hrIndex > 0) {
                            // 计算时间间隔（秒）
                            double timeInterval = (heartRate.get(hrIndex).getPosition() -
                                    heartRate.get(hrIndex - 1).getPosition()) * 0.01;

                            // 计算应激强度: 心率乘时间
                            stressValue += s.get(j) * timeInterval * 0.02;
                        }
                    }

                    // 记录应激强度点
                    stressPoints.add(new StressPoint(
                            segment.getStartIndex() + startIdx,
                            segment.getStartIndex() + endIdx,
                            stressValue,
                            0));

                    numm++;
                }
            }
        }

        System.out.println("FCLP段应激强度计算完成，共计算 " + stressPoints.size() + " 个应激强度点");
    }

    /**
     * 提取指定段的心率值
     */
    private List<Double> extractHeartRateValues(FCLPDetector.FCLPSegment segment) {
        return IntStream.range(segment.getStartIndex(), segment.getEndIndex() + 1)
                .mapToObj(i -> heartRate.get(i).getHeartRate())
                .collect(Collectors.toList());
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
     * 寻找过零点
     */
    private List<Integer> findZeroCrossings(List<Double> data) {
        List<Integer> zeroCrossings = new ArrayList<>();

        for (int i = 0; i < data.size() - 1; i++) {
            double current = data.get(i);
            double next = data.get(i + 1);

            // 检查是否过零点
            if (Math.signum(current) != Math.signum(next)) {
                zeroCrossings.add(i);
            }
        }

        return zeroCrossings;
    }

    /**
     * 获取应激强度数据
     */
    public List<StressPoint> getStressPoints() {
        return stressPoints;
    }

    /**
     * 可视化应激强度
     */
    public void visualizeStress() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("FCLP段应激强度");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new StressPanel());
            frame.setVisible(true);
        });
    }

    /**
     * 应激强度可视化面板
     */
    class StressPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int padding = 50;
            int chartWidth = width - 2 * padding;
            int chartHeight = height - 2 * padding;

            // 绘制坐标轴
            g2.drawLine(padding, padding, padding, height - padding);
            g2.drawLine(padding, height - padding, width - padding, height - padding);

            // 绘制标题和标签
            g2.drawString("FCLP段应激强度", width / 2 - 40, padding / 2);
            g2.drawString("时间(秒)", width / 2, height - padding / 2);
            g2.drawString("应激强度", padding / 2, height / 2);

            if (stressPoints.isEmpty()) return;

            // 找到数据范围
            double minStress = stressPoints.stream()
                    .mapToDouble(StressPoint::getStressValue)
                    .min().orElse(0);
            double maxStress = stressPoints.stream()
                    .mapToDouble(StressPoint::getStressValue)
                    .max().orElse(1);

            int minPosition = stressPoints.stream()
                    .mapToInt(StressPoint::getStartIndex)
                    .min().orElse(0);
            int maxPosition = stressPoints.stream()
                    .mapToInt(StressPoint::getEndIndex)
                    .max().orElse(1);

            double xScale = (double) chartWidth / (maxPosition - minPosition);
            double yScale = (double) chartHeight / (maxStress - minStress);

            // 绘制应激强度柱状图
            g2.setColor(Color.BLUE);
            for (StressPoint point : stressPoints) {
                double x1 = padding + (point.getStartIndex() - minPosition) * xScale;
                double x2 = padding + (point.getEndIndex() - minPosition) * xScale;
                double y = height - padding - (point.getStressValue() - minStress) * yScale;

                // 绘制矩形表示应激强度
                g2.fillRect((int) x1, (int) y, (int) (x2 - x1), (int) (height - padding - y));

                // 添加标签
                g2.drawString(String.format("%.2f", point.getStressValue()),
                        (int) ((x1 + x2) / 2 - 15), (int) y - 5);
            }

            // 添加图例
            g2.drawString("应激强度（蓝色）", width - 150, padding + 20);
        }
    }
}