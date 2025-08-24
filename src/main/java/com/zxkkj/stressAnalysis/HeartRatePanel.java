package com.zxkkj.stressAnalysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

public class HeartRatePanel extends JPanel {
    private static List<RRIntervalCalculator.RRIntervalPoint> heartRate;

    private static List<RRIntervalCalculator.RRIntervalPoint> rrIntervals;

    public HeartRatePanel(List<RRIntervalCalculator.RRIntervalPoint> heartRate,
                          List<RRIntervalCalculator.RRIntervalPoint> rrIntervals) {
        this.heartRate = heartRate;
        this.rrIntervals = rrIntervals;
    }

    public void plotHeartRate() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("心率");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new HeartRatePanel(heartRate,null));
            frame.setVisible(true);
        });
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
        g2.drawString("心率曲线", width / 2 - 30, padding / 2);
        g2.drawString("时间(秒)", width / 2, height - padding / 2);
        g2.drawString("心率(bpm)", padding / 2, height / 2);

        if (heartRate.isEmpty() || rrIntervals.isEmpty()) return;

        // 找到数据范围
        double minHR = Math.min(
                heartRate.stream().mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).min().orElse(0),
                rrIntervals.stream().mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).min().orElse(0)
        );
        double maxHR = Math.max(
                heartRate.stream().mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).max().orElse(1),
                rrIntervals.stream().mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).max().orElse(1)
        );

        int minPosition = Math.min(
                heartRate.stream().mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition).min().orElse(0),
                rrIntervals.stream().mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition).min().orElse(0)
        );
        int maxPosition = Math.max(
                heartRate.stream().mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition).max().orElse(1),
                rrIntervals.stream().mapToInt(RRIntervalCalculator.RRIntervalPoint::getPosition).max().orElse(1)
        );

        double xScale = (double) chartWidth / (maxPosition - minPosition);
        double yScale = (double) chartHeight / (maxHR - minHR);

        // 绘制计算得心率曲线（黑色）
        g2.setColor(Color.BLACK);
        Path2D path1 = new Path2D.Double();
        boolean firstPoint1 = true;

        for (RRIntervalCalculator.RRIntervalPoint point : rrIntervals) {
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

        for (RRIntervalCalculator.RRIntervalPoint point : heartRate) {
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
