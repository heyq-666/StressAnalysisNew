package com.zxkkj.stressAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

public class RRIntervalCalculator {
    private final List<Double> ecgData;
    private final List<RRIntervalPoint> rrIntervals = new ArrayList<>();
    private final List<RRIntervalPoint> heartRate = new ArrayList<>();

    // 参数配置
    private static final int SEGMENT1 = 500; // 心电波判断长度（采样点数）
    private static final int SEGMENT0 = 100; // 心电波判断的步进长度（1秒）
    private static final double SAMPLE_INTERVAL = 0.01; // 采样间隔（秒）
    private static final double PEAK_HEIGHT_RATIO_ECG = 0.6; // 心电峰值高度比例
    private static final double PEAK_HEIGHT_RATIO_DIFF = 0.38; // 心电差分峰值高度比例
    private static final int MAX_HR_CHANGE = 10; // 最大心率变化阈值

    public RRIntervalCalculator(List<Double> ecgData) {
        this.ecgData = ecgData;
    }

    /**
     * 计算RR间期和心率
     */
    public void calculateRRIntervals() {
        // 计算心电差分波
        List<Double> diffEcg = calculateDifference(ecgData);

        int i = 0; // 起始心电波位置
        int numRR = 0; // RR间期计数
        int goodBadValue = 1; // 心电数据好坏判据初值
        int lastNoZeroIndex = -1; // 记住非0心率段最后一个心率的索引

        while (i < ecgData.size()) {
            if (i + SEGMENT1 <= ecgData.size()) {
                // 提取一段心电波形
                List<Double> segment = ecgData.subList(i, i + SEGMENT1);
                List<Double> diffSegment = diffEcg.subList(i, i + SEGMENT1 - 1);

                // 寻找峰值
                List<Integer> ecgPeaks = findPeaks(segment, PEAK_HEIGHT_RATIO_ECG);
                List<Integer> diffPeaks = findPeaks(diffSegment, PEAK_HEIGHT_RATIO_DIFF);

                // 计算心电波信号和心电波差分信号的峰值数目之差的绝对值
                int goodBad = Math.abs(ecgPeaks.size() - diffPeaks.size());

                if (goodBad < goodBadValue) {
                    // 数据可用，求RR间期和心率
                    for (int k = 0; k < diffPeaks.size() - 1; k++) {
                        double rrInterval = (diffPeaks.get(k + 1) - diffPeaks.get(k)) * SAMPLE_INTERVAL;
                        double hr = 60.0 / rrInterval;
                        int position = i + diffPeaks.get(k);

                        // 剔除错误数据
                        if (goodBadValue != 1) { // 非首个非0心率值
                            if (k == 0) {
                                // 该段的第1个心率数据，与前段最后1个心率数据比较
                                if (lastNoZeroIndex >= 0 &&
                                        Math.abs(hr - heartRate.get(lastNoZeroIndex).getHeartRate()) > MAX_HR_CHANGE) {
                                    // 心率变化超过阈值，使用前段最后时刻的心率
                                    rrInterval = heartRate.get(lastNoZeroIndex).getRrInterval();
                                    hr = 60.0 / rrInterval;
                                }
                            } else {
                                // 不是该段的第1个心率数据，与前时刻心率比较
                                if (Math.abs(hr - heartRate.get(numRR - 1).getHeartRate()) > MAX_HR_CHANGE) {
                                    // 心率变化超过阈值，使用前刻心率
                                    rrInterval = heartRate.get(numRR - 1).getRrInterval();
                                    hr = 60.0 / rrInterval;
                                }
                            }
                        }

                        // 添加RR间期和心率数据
                        rrIntervals.add(new RRIntervalPoint(rrInterval, hr, position));
                        heartRate.add(new RRIntervalPoint(rrInterval, hr, position));
                        lastNoZeroIndex = numRR;
                        numRR++;

                        // 更改阈值（初始阈值是为了确保第1个心率值正确）
                        goodBadValue = 3;
                    }

                    i += SEGMENT1; // 判断指针移动segment1个采样点，继续判断
                } else {
                    // 心电数据很差，无法使用，无法计算RR间期和心率
                    i += SEGMENT0; // 判断指针移动segment0个采样点，继续判断

                    // 添加0心率数据
                    rrIntervals.add(new RRIntervalPoint(0, 0, i - 1));
                    heartRate.add(new RRIntervalPoint(0, 0, i - 1));
                    numRR++;
                }
            } else {
                // 若不够一个判断长度，则舍弃该段，结束判断
                break;
            }
        }

        // 修正高心率插补0心率
        correctHeartRate();

        System.out.println("RR间期和心率计算完成，共计算 " + rrIntervals.size() + " 个数据点");
    }

    /**
     * 计算数据的差分
     */
    private List<Double> calculateDifference(List<Double> data) {
        return IntStream.range(0, data.size() - 1)
                .mapToDouble(i -> data.get(i + 1) - data.get(i))
                .boxed()
                .collect(Collectors.toList());
    }

    /**
     * 寻找峰值
     */
    private List<Integer> findPeaks(List<Double> data, double heightRatio) {
        List<Integer> peaks = new ArrayList<>();

        if (data.isEmpty()) {
            return peaks;
        }

        // 计算峰值高度阈值
        double maxValue = data.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minHeight = maxValue * heightRatio;

        // 寻找峰值
        for (int i = 1; i < data.size() - 1; i++) {
            if (data.get(i) > data.get(i - 1) &&
                    data.get(i) > data.get(i + 1) &&
                    data.get(i) > minHeight) {
                peaks.add(i);
            }
        }

        return peaks;
    }

    /**
     * 修正心率数据
     */
    private void correctHeartRate() {
        // 找到非0心率位置
        List<Integer> nonZeroIndices = IntStream.range(0, heartRate.size())
                .filter(i -> heartRate.get(i).getHeartRate() != 0)
                .boxed()
                .collect(Collectors.toList());

        if (nonZeroIndices.isEmpty()) {
            System.out.println("采集信号无效，无有效心率");
            return;
        }

        // 修正第一个心率值
        RRIntervalPoint firstPoint = heartRate.get(nonZeroIndices.get(0));
        if (firstPoint.getHeartRate() > 200) {
            double rrInterval = 60.0 / 200; // 对应200次/分钟的RR间期
            heartRate.set(nonZeroIndices.get(0),
                    new RRIntervalPoint(rrInterval, 200, firstPoint.getPosition()));
        }

        // 修正其他高心率值
        for (int i = 1; i < heartRate.size(); i++) {
            RRIntervalPoint current = heartRate.get(i);
            if (current.getHeartRate() > 200) {
                RRIntervalPoint previous = heartRate.get(i - 1);
                heartRate.set(i, new RRIntervalPoint(
                        previous.getRrInterval(),
                        previous.getHeartRate(),
                        current.getPosition()
                ));
            }
        }

        // 插补0心率
        int firstNonZeroIndex = nonZeroIndices.get(0);
        for (int i = firstNonZeroIndex; i < heartRate.size(); i++) {
            RRIntervalPoint current = heartRate.get(i);
            if (current.getHeartRate() == 0 && i > 0) {
                RRIntervalPoint previous = heartRate.get(i - 1);
                heartRate.set(i, new RRIntervalPoint(
                        previous.getRrInterval(),
                        previous.getHeartRate(),
                        current.getPosition()
                ));
            }
        }

        // 删除首个非0心率前的数据
        if (firstNonZeroIndex > 0) {
            heartRate.subList(0, firstNonZeroIndex).clear();
        }
    }

    /**
     * 获取RR间期数据
     */
    public List<RRIntervalPoint> getRRIntervals() {
        return rrIntervals;
    }

    /**
     * 获取心率数据
     */
    public List<RRIntervalPoint> getHeartRate() {
        return heartRate;
    }

    /**
     * RR间期和心率数据点类
     */
    public static class RRIntervalPoint {
        private final double rrInterval;
        private final double heartRate;
        private final int position;

        public RRIntervalPoint(double rrInterval, double heartRate, int position) {
            this.rrInterval = rrInterval;
            this.heartRate = heartRate;
            this.position = position;
        }

        public double getRrInterval() {
            return rrInterval;
        }

        public double getHeartRate() {
            return heartRate;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return String.format("RRIntervalPoint{RR=%.3fs, HR=%.1fbpm, pos=%d}",
                    rrInterval, heartRate, position);
        }
    }

    /**
     * 可视化心率曲线
     */
    public void visualizeHeartRate() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("心率曲线");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new HeartRatePanel());
            frame.setVisible(true);
        });
    }

    /**
     * 心率数据面板
     */
    class HeartRatePanel extends JPanel {
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
            g2.drawString("心率曲线", width / 2 - 30, padding / 2);
            g2.drawString("时间(秒)", width / 2, height - padding / 2);
            g2.drawString("心率(bpm)", padding / 2, height / 2);

            if (heartRate.isEmpty() || rrIntervals.isEmpty()) return;

            // 找到数据范围
            double minHR = Math.min(
                    heartRate.stream().mapToDouble(RRIntervalPoint::getHeartRate).min().orElse(0),
                    rrIntervals.stream().mapToDouble(RRIntervalPoint::getHeartRate).min().orElse(0)
            );
            double maxHR = Math.max(
                    heartRate.stream().mapToDouble(RRIntervalPoint::getHeartRate).max().orElse(1),
                    rrIntervals.stream().mapToDouble(RRIntervalPoint::getHeartRate).max().orElse(1)
            );

            int minPosition = Math.min(
                    heartRate.stream().mapToInt(RRIntervalPoint::getPosition).min().orElse(0),
                    rrIntervals.stream().mapToInt(RRIntervalPoint::getPosition).min().orElse(0)
            );
            int maxPosition = Math.max(
                    heartRate.stream().mapToInt(RRIntervalPoint::getPosition).max().orElse(1),
                    rrIntervals.stream().mapToInt(RRIntervalPoint::getPosition).max().orElse(1)
            );

            double xScale = (double) chartWidth / (maxPosition - minPosition);
            double yScale = (double) chartHeight / (maxHR - minHR);

            // 绘制计算得心率曲线（黑色）
            g2.setColor(Color.BLACK);
            Path2D path1 = new Path2D.Double();
            boolean firstPoint1 = true;

            for (RRIntervalPoint point : rrIntervals) {
                double x = padding + (point.getPosition() - minPosition) * xScale;
                double y = height - padding - (point.getHeartRate() - minHR) * yScale;

                if (firstPoint1) {
                    path1.moveTo(x, y);
                    firstPoint1 = false;
                } else {
                    path1.lineTo(x, y);
                }

                // 绘制数据点
                g2.fillOval((int) x - 2, (int) y - 2, 4, 4);
            }

            g2.draw(path1);

            // 绘制插补后心率曲线（红色）
            g2.setColor(Color.RED);
            Path2D path2 = new Path2D.Double();
            boolean firstPoint2 = true;

            for (RRIntervalPoint point : heartRate) {
                double x = padding + (point.getPosition() - minPosition) * xScale;
                double y = height - padding - (point.getHeartRate() - minHR) * yScale;

                if (firstPoint2) {
                    path2.moveTo(x, y);
                    firstPoint2 = false;
                } else {
                    path2.lineTo(x, y);
                }

                // 绘制数据点
                g2.fillOval((int) x - 2, (int) y - 2, 4, 4);
            }

            g2.draw(path2);

            // 添加图例
            g2.setColor(Color.BLACK);
            g2.drawString("计算得心率曲线（黑色）", width - 180, padding + 20);
            g2.setColor(Color.RED);
            g2.drawString("插补后心率曲线（红色）", width - 180, padding + 40);
        }
    }
}