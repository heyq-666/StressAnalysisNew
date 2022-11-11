package com.zxkkj.stressAnalysis.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.zxkkj.stressAnalysis.model.PeakModel;
import com.zxkkj.stressAnalysis.model.RRData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CommonUtils {

    public static Double calculateMaxValue(List<Double> ecgList) {

        if (CollectionUtil.isEmpty(ecgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            Double maxValue = ecgList.stream().max(Comparator.comparing(Double::doubleValue)).get();
            return maxValue;
        }
    }

    public static Double calculateMinValue(List<Double> ecgList) {

        if (CollectionUtil.isEmpty(ecgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            Double minValue = ecgList.stream().min(Comparator.comparing(Double::doubleValue)).get();
            return minValue;
        }
    }


    public static Integer calculateMaxValueInteger(List<Integer> list) {

        if (CollectionUtil.isEmpty(list)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            Integer maxValue = list.stream().max(Comparator.comparing(Integer::intValue)).get();
            return maxValue;
        }
    }

    public static Integer calculateMinValueInteger(List<Integer> list) {

        if (CollectionUtil.isEmpty(list)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            Integer minValue = list.stream().min(Comparator.comparing(Integer::intValue)).get();
            return minValue;
        }
    }

    public static double sub(double value1, double value2) {

        BigDecimal b1 = new BigDecimal(Double.toString(value1));

        BigDecimal b2 = new BigDecimal(Double.toString(value2));

        BigDecimal b = new BigDecimal(b1.subtract(b2).doubleValue());

        return b.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double mul(double value1, double value2) {

        BigDecimal b1 = new BigDecimal(Double.toString(value1));

        BigDecimal b2 = new BigDecimal(Double.toString(value2));

        BigDecimal b = new BigDecimal(b1.multiply(b2).doubleValue());

        return b.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double division(double value1, double value2){

        BigDecimal b1 = new BigDecimal(Double.toString(value1));

        BigDecimal b2 = new BigDecimal(Double.toString(value2));

        return b1.divide(b2,2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double avg(List<Double> list){

        double avg = list.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
        return new BigDecimal(avg).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();

    }
    /**
     * 平滑滤波
     * @param list
     * @return
     */
    public static List<RRData> smooth(List<RRData> list){

        if (CollectionUtil.isEmpty(list)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            List<RRData> smoothList = list;
            int window = 5;//平滑窗口
            int length = smoothList.size();

            if (length == 3){
                smoothList.get(length - 2).setHr(division((smoothList.get(0).getHr()+smoothList.get(1).getHr()+smoothList.get(2).getHr()),3.0));
            }
            if (length == 4){
                smoothList.get(length - 3).setHr(division((smoothList.get(0).getHr()+smoothList.get(1).getHr()+smoothList.get(2).getHr()),3.0));
                smoothList.get(length - 2).setHr(division((smoothList.get(1).getHr()+smoothList.get(2).getHr()+smoothList.get(3).getHr()),3.0));
            }
            if (length >= window){
                smoothList.get(1).setHr(division((smoothList.get(0).getHr()+smoothList.get(1).getHr()+smoothList.get(2).getHr()),3.0));
                for (int i = 2; i < smoothList.size() - 2; i++) {
                    smoothList.get(i).setHr(division((smoothList.get(i-2).getHr()+smoothList.get(i-1).getHr()+smoothList.get(i).getHr()+smoothList.get(i+1).getHr()+smoothList.get(i+2).getHr()),5.0));
                }
                smoothList.get(length - 2).setHr(division((smoothList.get(length - 3).getHr() + smoothList.get(length - 2).getHr() + smoothList.get(length - 1).getHr()),3.0));
            }
            return smoothList;
        }
    }

    /**
     * 平滑滤波
     * @param list
     * @param start
     * @param end
     * @param window
     * @return
     */
    public static List<RRData> smoothNew(List<RRData> list, int start,int end, int window) {

        if (CollectionUtil.isEmpty(list) || start > end || list.size() <= start || list.size() <= end) {
            throw new RuntimeException("参数错误");
        }

        List<RRData> listNew = new ArrayList<>();
        RRData rrData = new RRData();
        rrData.setHr(new BigDecimal(list.get(0).getHr()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
        rrData.setRRIntervalData(list.get(0).getRRIntervalData());
        rrData.setSamplingNum(list.get(0).getSamplingNum());
        listNew.add(rrData);
        for (int i = start + 1; i < end; i++) {
            double avg = 0.0;
            if (i-start < window/2) {
                int step = i-start;
                avg = list.stream().skip(start).limit(2 * step + 1).mapToDouble(RRData::getHr).sum() / (2 * step + 1);
            } else if (end - i < window/2){
                int step = end - i;
                avg = list.stream().skip(i - step).limit(2*step + 1).mapToDouble(RRData::getHr).sum() / (2 * step + 1);
            } else {
                avg = list.stream().skip(i - window/2).limit(window).mapToDouble(RRData::getHr).sum() / window;
            }
            RRData rrData1 = new RRData();
            rrData1.setHr(new BigDecimal(avg).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
            rrData1.setRRIntervalData(list.get(i).getRRIntervalData());
            rrData1.setSamplingNum(list.get(i).getSamplingNum());
            listNew.add(rrData1);
        }
        RRData rrDataEnd = new RRData();
        rrDataEnd.setHr(list.get(list.size()-1).getHr());
        rrDataEnd.setRRIntervalData(list.get(list.size()-1).getRRIntervalData());
        rrDataEnd.setSamplingNum(list.get(list.size()-1).getSamplingNum());
        listNew.add(rrDataEnd);
        return listNew;
    }

    /**
     * 心率均值
     * @param rrListSub
     * @return
     */
    public static double mean(List<RRData> rrListSub) {

        double hrAvg = rrListSub.stream().mapToDouble(RRData::getHr).average().getAsDouble();
        return hrAvg;
    }

    public static double randomDouble(double start,double end){
        double result = new BigDecimal(Math.random() * (end - start) + start).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public static List<PeakModel> findPeakList(List<Double> zxxEcgList) {

        if (CollectionUtil.isEmpty(zxxEcgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            int flag = 0;
            List<PeakModel> peakList = new ArrayList<>();
            for (int i = 0; i < zxxEcgList.size() - 1; i++) {//只记录波峰及位置
                if (zxxEcgList.get(i + 1).compareTo(zxxEcgList.get(i)) > 0){
                    flag = 2;
                }else if (zxxEcgList.get(i + 1).compareTo(zxxEcgList.get(i)) < 0){
                    if (flag == 2){
                        PeakModel peakModel = new PeakModel();
                        peakModel.setPeakValue(zxxEcgList.get(i));
                        peakModel.setIndex(i);
                        peakList.add(peakModel);
                    }
                    flag = 1;
                }
            }
            return peakList;
        }
    }
}