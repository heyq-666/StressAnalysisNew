package com.zxkkj.stressAnalysis.panel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class BreathDataPanel extends JPanel {
    private List<Double> breathData = new ArrayList<>();
    public BreathDataPanel(List<Double> breathData) {
        this.breathData = breathData;
    }

    public void plotBreathData() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("呼吸波曲线");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new BreathDataPanel(breathData));
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
        g2.drawString("呼吸波曲线", width / 2 - 30, padding / 2);
        g2.drawString("秒", width / 2, height - padding / 2);
        g2.drawString("呼吸波值", padding / 2, height / 2);

        if (breathData.isEmpty()) return;

        // 找到数据范围
        double min = breathData.stream().min(Double::compare).orElse(0.0);
        double max = breathData.stream().max(Double::compare).orElse(1.0);

        // 绘制数据
        g2.setColor(Color.RED);
        Path2D path = new Path2D.Double();
        double xScale = (double) chartWidth / breathData.size();
        double yScale = (double) chartHeight / (max - min);

        for (int i = 0; i < breathData.size(); i++) {
            double x = padding + i * xScale;
            double y = height - padding - (breathData.get(i) - min) * yScale;

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        g2.draw(path);
    }
}
