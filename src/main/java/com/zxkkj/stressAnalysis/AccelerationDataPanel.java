package com.zxkkj.stressAnalysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class AccelerationDataPanel extends JPanel {
    private List<Double[]> yzxData = new ArrayList<>();

    public AccelerationDataPanel(List<Double[]> yzxData) {
        this.yzxData = yzxData;
    }

    public void plotAccelerationData() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("加速度曲线");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new AccelerationDataPanel(yzxData));
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
        g2.drawString("加速度曲线", width / 2 - 30, padding / 2);
        g2.drawString("秒", width / 2, height - padding / 2);
        g2.drawString("加速度值", padding / 2, height / 2);

        if (yzxData.isEmpty()) return;

        // 找到数据范围
        double min = yzxData.stream()
                .flatMapToDouble(arr -> java.util.Arrays.stream(arr)
                        .mapToDouble(Double::doubleValue))
                .min().orElse(0);
        double max = yzxData.stream()
                .flatMapToDouble(arr -> java.util.Arrays.stream(arr)
                        .mapToDouble(Double::doubleValue))
                .max().orElse(1);

        // 绘制数据
        double xScale = (double) chartWidth / yzxData.size();
        double yScale = (double) chartHeight / (max - min);

        // 绘制加速度幅值（红色）
        g2.setColor(Color.RED);
        drawAccelerationData(g2, 3, padding, chartHeight, xScale, yScale, min);

        // 绘制Y轴（蓝色）
        g2.setColor(Color.BLUE);
        drawAccelerationData(g2, 0, padding, chartHeight, xScale, yScale, min);

        // 绘制Z轴（绿色）
        g2.setColor(Color.GREEN);
        drawAccelerationData(g2, 1, padding, chartHeight, xScale, yScale, min);

        // 绘制X轴（黑色）
        g2.setColor(Color.BLACK);
        drawAccelerationData(g2, 2, padding, chartHeight, xScale, yScale, min);

        // 绘制图例
        g2.drawString("加速度幅值（红色）", width - 150, padding + 20);
        g2.setColor(Color.BLUE);
        g2.drawString("Y轴（蓝色）", width - 150, padding + 40);
        g2.setColor(Color.GREEN);
        g2.drawString("Z轴（绿色）", width - 150, padding + 60);
        g2.setColor(Color.BLACK);
        g2.drawString("X轴（黑色）", width - 150, padding + 80);
    }

    private void drawAccelerationData(Graphics2D g2, int index, int padding, int chartHeight,
                                      double xScale, double yScale, double min) {
        Path2D path = new Path2D.Double();
        int height = getHeight();

        for (int i = 0; i < yzxData.size(); i++) {
            double x = padding + i * xScale;
            double y = height - padding - (yzxData.get(i)[index] - min) * yScale;

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        g2.draw(path);
    }
}
