package com.zxkkj.stressAnalysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FCLPDetector {
    private final List<Double> heartRate;
    private final List<FCLPSegment> fclpSegments = new ArrayList<>();

    // 参数配置
    private static final int SEGMENT1 = 2000; // 段落心率数
    private static final int SEGMENT0 = 100;  // 段落递进心率数
    private static final int MIN_ZERO_CROSSINGS = 3; // 最小过零点数
    private static final int MIN_INTERVAL = 105;     // 最小间隔数
    private static final int MAX_INTERVAL_DIFF = 200; // 最大间隔差

    public FCLPDetector(List<Double> heartRate) {
        this.heartRate = heartRate;
    }

    /**
     * 检测FCLP段
     */
    public void detectFCLPSegments() {
        int i = 0; // 起始心率位置
        int fclpNum = 0; // FCLP段序号

        while (i < heartRate.size()) {
            if (i + SEGMENT1 <= heartRate.size()) {
                // 如果从i起始的心率构成一个段落长度，则进行判断处理
                if (isFCLPSegment(i, i + SEGMENT1 - 1)) {
                    fclpSegments.add(new FCLPSegment(i, i + SEGMENT1 - 1));
                    fclpNum++;
                    i += SEGMENT1; // 判断指针移动segment1个心率点，继续判断
                } else {
                    i += SEGMENT0; // 判断指针移动segment0个心率点，继续判断
                }
            } else {
                // 若不够一个判断长度，则按segment0心率长度进行判断
                int endIndex = Math.min(i + SEGMENT0, heartRate.size());
                if (endIndex - i > SEGMENT0 / 2) { // 如果长度大于segment0的一半，进行判断
                    if (isFCLPSegment(i, endIndex - 1)) {
                        fclpSegments.add(new FCLPSegment(i, endIndex - 1));
                        fclpNum++;
                    }
                }
                i = heartRate.size(); // 判断指针移动到结尾处
            }
        }

        System.out.println("FCLP段识别完成，共识别 " + fclpSegments.size() + " 个FCLP段");
    }

    /**
     * 判断指定区间是否为FCLP段
     */
    private boolean isFCLPSegment(int start, int end) {
        // 提取心率数据
        List<Double> segment = heartRate.subList(start, end + 1);

        // 将心率数据进行平滑滤波并去均值
        List<Double> smoothed = smoothData(segment, 100);
        double mean = smoothed.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        List<Double> normalized = smoothed.stream()
                .map(val -> val - mean)
                .collect(Collectors.toList());

        // 找出所有过零点
        List<Integer> zeroCrossings = findZeroCrossings(normalized);

        if (zeroCrossings.size() < MIN_ZERO_CROSSINGS) {
            return false; // 过零点数不足
        }

        // 计算过零点之间的间隔
        List<Integer> intervals = new ArrayList<>();
        for (int i = 0; i < zeroCrossings.size() - 1; i++) {
            intervals.add(zeroCrossings.get(i + 1) - zeroCrossings.get(i));
        }

        if (intervals.isEmpty()) {
            return false; // 没有间隔数据
        }

        // 找到最小和最大间隔
        int minInterval = intervals.stream().min(Integer::compare).orElse(0);
        int maxInterval = intervals.stream().max(Integer::compare).orElse(0);

        // 判断是否为FCLP段
        return minInterval > MIN_INTERVAL && (maxInterval - minInterval) < MAX_INTERVAL_DIFF;
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
     * 获取FCLP段数据
     */
    public List<FCLPSegment> getFCLPSegments() {
        return fclpSegments;
    }

    /**
     * FCLP段数据类
     */
    public static class FCLPSegment {
        private final int startIndex;
        private final int endIndex;

        public FCLPSegment(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        @Override
        public String toString() {
            return String.format("FCLPSegment{start=%d, end=%d}", startIndex, endIndex);
        }
    }

    /**
     * 可视化FCLP段
     */
    public void visualizeFCLPSegments(List<RRIntervalCalculator.RRIntervalPoint> heartRatePoints) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("FCLP段识别结果");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new FCLPPanel(heartRatePoints));
            frame.setVisible(true);
        });
    }

    /**
     * FCLP段可视化面板
     */
    class FCLPPanel extends JPanel {
        private final List<RRIntervalCalculator.RRIntervalPoint> heartRatePoints;

        public FCLPPanel(List<RRIntervalCalculator.RRIntervalPoint> heartRatePoints) {
            this.heartRatePoints = heartRatePoints;
        }

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
            g2.drawString("FCLP段识别结果", width / 2 - 40, padding / 2);
            g2.drawString("时间(秒)", width / 2, height - padding / 2);
            g2.drawString("心率(bpm)", padding / 2, height / 2);

            if (heartRatePoints.isEmpty() || fclpSegments.isEmpty()) return;

            // 找到数据范围
            double minHR = heartRatePoints.stream()
                    .mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate)
                    .min().orElse(0);
            double maxHR = heartRatePoints.stream()
                    .mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate)
                    .max().orElse(1);

            int minPosition = heartRatePoints.stream()
                    .mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition)
                    .min().orElse(0);
            int maxPosition = heartRatePoints.stream()
                    .mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition)
                    .max().orElse(1);

            double xScale = (double) chartWidth / (maxPosition - minPosition);
            double yScale = (double) chartHeight / (maxHR - minHR);

            // 绘制心率曲线
            g2.setColor(Color.BLUE);
            Path2D path = new Path2D.Double();
            boolean firstPoint = true;

            for (RRIntervalCalculator.RRIntervalPoint point : heartRatePoints) {
                double x = padding + (point.getPosition() - minPosition) * xScale;
                double y = height - padding - (point.getHeartRate() - minHR) * yScale;

                if (firstPoint) {
                    path.moveTo(x, y);
                    firstPoint = false;
                } else {
                    path.lineTo(x, y);
                }
            }

            g2.draw(path);

            // 标记FCLP段
            g2.setColor(Color.RED);
            for (FCLPSegment segment : fclpSegments) {
                if (segment.getStartIndex() < heartRatePoints.size() &&
                        segment.getEndIndex() < heartRatePoints.size()) {

                    RRIntervalCalculator.RRIntervalPoint startPoint = heartRatePoints.get(segment.getStartIndex());
                    RRIntervalCalculator.RRIntervalPoint endPoint = heartRatePoints.get(segment.getEndIndex());

                    double startX = padding + (startPoint.getPosition() - minPosition) * xScale;
                    double endX = padding + (endPoint.getPosition() - minPosition) * xScale;

                    // 绘制FCLP段标记
                    g2.drawLine((int) startX, padding, (int) startX, height - padding);
                    g2.drawLine((int) endX, padding, (int) endX, height - padding);

                    // 添加FCLP段标签
                    g2.drawString("FCLP", (int) ((startX + endX) / 2 - 15), padding - 10);
                }
            }

            // 添加图例
            g2.setColor(Color.BLUE);
            g2.drawString("心率曲线（蓝色）", width - 150, padding + 20);
            g2.setColor(Color.RED);
            g2.drawString("FCLP段标记（红色）", width - 150, padding + 40);
        }
    }
}