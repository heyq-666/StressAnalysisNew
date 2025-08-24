package com.zxkkj.stressAnalysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public class ECGDataPanel extends JPanel {
    private List<Double> ecgData = new ArrayList<>();

    public ECGDataPanel(List<Double> ecgData) {
        this.ecgData = ecgData;
    }

    public void plotECGData() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("心电曲线");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(800, 600);
            frame.add(new ECGDataPanel(ecgData));
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
        g2.drawString("心电曲线", width / 2 - 30, padding / 2);
        g2.drawString("秒", width / 2, height - padding / 2);
        g2.drawString("心电值", padding / 2, height / 2);

        if (ecgData.isEmpty()) return;

        // 找到数据范围
        double min = ecgData.stream().min(Double::compare).orElse(0.0);
        double max = ecgData.stream().max(Double::compare).orElse(1.0);

        // 绘制数据
        g2.setColor(Color.RED);
        Path2D path = new Path2D.Double();
        double xScale = (double) chartWidth / ecgData.size();
        double yScale = (double) chartHeight / (max - min);

        for (int i = 0; i < ecgData.size(); i++) {
            double x = padding + i * xScale;
            double y = height - padding - (ecgData.get(i) - min) * yScale;

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        g2.draw(path);
    }
}
