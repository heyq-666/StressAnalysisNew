package com.zxkkj.stressAnalysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class BreathRatePanel extends JPanel {
    private List<BreathRateCalculator.BreathRatePoint> breathRate = new ArrayList<>();

    public BreathRatePanel(List<BreathRateCalculator.BreathRatePoint> breathRate) {
        this.breathRate = breathRate;
    }

    public void plotBreathRate() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("呼吸率");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new BreathRatePanel(breathRate));
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
        g2.drawString("呼吸率曲线", width / 2 - 30, padding / 2);
        g2.drawString("位置", width / 2, height - padding / 2);
        g2.drawString("呼吸率(次/分钟)", padding / 2, height / 2);

        if (breathRate.isEmpty()) return;

        // 找到数据范围
        double minRate = breathRate.stream()
                .mapToDouble(BreathRateCalculator.BreathRatePoint::getRate)
                .min().orElse(0);
        double maxRate = breathRate.stream()
                .mapToDouble(BreathRateCalculator.BreathRatePoint::getRate)
                .max().orElse(1);

        int minPosition = breathRate.stream()
                .mapToInt(BreathRateCalculator.BreathRatePoint::getPosition)
                .min().orElse(0);
        int maxPosition = breathRate.stream()
                .mapToInt(BreathRateCalculator.BreathRatePoint::getPosition)
                .max().orElse(1);

        // 绘制数据
        g2.setColor(Color.GREEN);
        Path2D path = new Path2D.Double();
        double xScale = (double) chartWidth / (maxPosition - minPosition);
        double yScale = (double) chartHeight / (maxRate - minRate);

        for (BreathRateCalculator.BreathRatePoint point : breathRate) {
            double x = padding + (point.getPosition() - minPosition) * xScale;
            double y = height - padding - (point.getRate() - minRate) * yScale;

            if (path.getCurrentPoint() == null) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }

            // 绘制数据点
            g2.fillOval((int) x - 2, (int) y - 2, 4, 4);
        }

        g2.draw(path);

        // 添加图例
        g2.drawString("呼吸率（绿色）", width - 150, padding + 20);
    }
}
