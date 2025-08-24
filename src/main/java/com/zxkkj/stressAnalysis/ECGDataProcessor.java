package com.zxkkj.stressAnalysis;

import lombok.Data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Data
public class ECGDataProcessor {
    // 数据包头和尾
    private static final byte[] F_PACKET_HEAD = {(byte) 0x55, (byte) 0xAA};
    private static final byte[] F_PACKET_TAIL = {(byte) 0xAA, (byte) 0x55};
    private static final int L_PACKET = 277;

    // 存储解析后的数据
    private List<Long> numECGData = new ArrayList<>();
    private List<Double> ecgData = new ArrayList<>();
    private List<Double> ecgMeanFilter = new ArrayList<>();
    private List<Double> breathData = new ArrayList<>();
    private List<Double[]> yzxData = new ArrayList<>();

    public void processData(String filename) throws IOException {
        // 读取文件数据
        byte[] dataPacket = Files.readAllBytes(Paths.get(filename));

        // 搜索所有数据帧头
        List<Integer> stposH = new ArrayList<>();
        for (int i = 0; i < dataPacket.length - 1; i++) {
            if (dataPacket[i] == F_PACKET_HEAD[0] && dataPacket[i + 1] == F_PACKET_HEAD[1]) {
                stposH.add(i);
            }
        }

        if (stposH.isEmpty()) {
            throw new IOException("未找到数据帧头");
        }

        int pFirst = stposH.get(0);
        int numFrame = 0;
        int pnext = pFirst;
        int dataLength = dataPacket.length;

        while (pnext < dataLength - L_PACKET - 1) {
            // 检查帧头和帧尾
            boolean headMatch = (dataPacket[pnext] == F_PACKET_HEAD[0] &&
                    dataPacket[pnext + 1] == F_PACKET_HEAD[1]);
            boolean tailMatch = (dataPacket[pnext + L_PACKET] == F_PACKET_TAIL[0] &&
                    dataPacket[pnext + L_PACKET + 1] == F_PACKET_TAIL[1]);

            if (headMatch && tailMatch) {
                // 提取ECG序号
                long frameNum = ((dataPacket[pnext + 2] & 0xFF) * (long)Math.pow(16, 6) +
                        (dataPacket[pnext + 3] & 0xFF) * (long)Math.pow(16, 4) +
                        (dataPacket[pnext + 4] & 0xFF) * (long)Math.pow(16, 2) +
                        (dataPacket[pnext + 5] & 0xFF));
                numECGData.add(frameNum);

                // 提取100个ECG数值
                for (int i = 0; i < 100; i++) {
                    double ecgValue = ((dataPacket[pnext + 13 + i * 2] & 0xFF) << 8) |
                            (dataPacket[pnext + 14 + i * 2] & 0xFF);
                    ecgData.add(ecgValue);
                }

                // 提取25个呼吸数值
                for (int i = 0; i < 25; i++) {
                    double breathValue = ((dataPacket[pnext + 214 + i * 2] & 0xFF) << 8) |
                            (dataPacket[pnext + 215 + i * 2] & 0xFF);
                    breathData.add(breathValue);
                }

                // 提取加速度数据
                double yAccel = parseAcceleration(dataPacket, pnext + 264) / 29250000.0;
                double zAccel = parseAcceleration(dataPacket, pnext + 268) / 29250000.0;
                double xAccel = parseAcceleration(dataPacket, pnext + 272) / 29250000.0;
                double magnitude = Math.sqrt(yAccel * yAccel + zAccel * zAccel + xAccel * xAccel);

                yzxData.add(new Double[]{yAccel, zAccel, xAccel, magnitude});

                pnext += 279;
                numFrame++;
            } else {
                pnext++;
            }
        }
    }
    private long parseAcceleration(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) |
                ((data[offset + 1] & 0xFFL) << 16) |
                ((data[offset + 2] & 0xFFL) << 8) |
                (data[offset + 3] & 0xFFL);
    }
    /**
     * ECG数据消脉冲干扰滤波
     */
    public void filterECGData() {
        // 计算ECG数据的平均值
        double mean = ecgData.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        for (Double value : ecgData) {
            ecgMeanFilter.add(value - mean);
        }

        double ecgMax = ecgMeanFilter.stream().mapToDouble(Math::abs).max().orElse(0.0);

        // 找到所有绝对值大于等于最大值的元素位置
        List<Integer> dataLarge = new ArrayList<>();
        for (int i = 0; i < ecgMeanFilter.size(); i++) {
            if (Math.abs(ecgMeanFilter.get(i)) >= ecgMax) {
                dataLarge.add(i);
            }
        }

        int lengthNormal = 500; // 正常ECG值判定的时间范围
        double normalECG;

        if (dataLarge.isEmpty()) {
            System.out.println("无脉冲干扰");
            return;
        }

        int firstLargeIndex = dataLarge.get(0);

        // 判断最大值后面是否有足够的时间范围
        if (firstLargeIndex + lengthNormal < ecgMeanFilter.size()) {
            // 取后续时间范围内的局部最大值作为判定值
            normalECG = IntStream.range(firstLargeIndex + 1, firstLargeIndex + lengthNormal + 1)
                    .mapToDouble(i -> Math.abs(ecgMeanFilter.get(i)))
                    .max()
                    .orElse(0.0);
        } else {
            // 取前面时间范围内的局部最大值作为判定值
            normalECG = IntStream.range(firstLargeIndex - lengthNormal, firstLargeIndex)
                    .mapToDouble(i -> Math.abs(ecgMeanFilter.get(i)))
                    .max()
                    .orElse(0.0);
        }

        // 如果此最大值小于判定值，则不存在脉冲干扰
        if (ecgMax < normalECG || ecgMax <= 200) {
            System.out.println("无脉冲干扰");
            return;
        }

        // 有脉冲干扰，进行剔除
        while (ecgMax >= 2 * normalECG || ecgMax > 200) {
            // 对所有最大值大于等于判定值的数据进行修正
            for (int i = 0; i < ecgMeanFilter.size(); i++) {
                if (Math.abs(ecgMeanFilter.get(i)) >= ecgMax) {
                    ecgMeanFilter.set(i, 0.0); // 将这些元素置为0
                }
            }

            // 再次进行均值平移
            double newMean = ecgMeanFilter.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            for (int i = 0; i < ecgMeanFilter.size(); i++) {
                ecgMeanFilter.set(i, ecgMeanFilter.get(i) - newMean);
            }

            // 再次获得心电的最大值
            ecgMax = ecgMeanFilter.stream()
                    .mapToDouble(Math::abs)
                    .max()
                    .orElse(0.0);

            // 再次获得所有大于等于该最大值的元素位置数组
            dataLarge.clear();
            for (int i = 0; i < ecgMeanFilter.size(); i++) {
                if (Math.abs(ecgMeanFilter.get(i)) >= ecgMax) {
                    dataLarge.add(i);
                }
            }

            if (dataLarge.isEmpty()) {
                break;
            }

            firstLargeIndex = dataLarge.get(0);

            // 更新normalECG
            if (firstLargeIndex + lengthNormal < ecgMeanFilter.size()) {
                normalECG = IntStream.range(firstLargeIndex + 1, firstLargeIndex + lengthNormal + 1)
                        .mapToDouble(i -> Math.abs(ecgMeanFilter.get(i)))
                        .max()
                        .orElse(0.0);
            } else {
                normalECG = IntStream.range(firstLargeIndex - lengthNormal, firstLargeIndex)
                        .mapToDouble(i -> Math.abs(ecgMeanFilter.get(i)))
                        .max()
                        .orElse(0.0);
            }
        }

        // 判断是否为反向R波，如果是则反向
        double maxPositive = ecgMeanFilter.stream().mapToDouble(Double::doubleValue).filter(d -> d > 0)
                .max().orElse(0.0);

        double minNegative = ecgMeanFilter.stream().mapToDouble(Double::doubleValue).filter(d -> d < 0)
                .min().orElse(0.0);

        if (Math.abs(maxPositive) < Math.abs(minNegative)) {
            // 反向处理
            for (int i = 0; i < ecgMeanFilter.size(); i++) {
                ecgMeanFilter.set(i, -ecgMeanFilter.get(i));
            }
        }
    }
}