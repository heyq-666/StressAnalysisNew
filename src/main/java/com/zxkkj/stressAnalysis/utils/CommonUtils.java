package com.zxkkj.stressAnalysis.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.zxkkj.stressAnalysis.model.RRIntervalPoint;

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

    public static double division(double value1, double value2){

        BigDecimal b1 = new BigDecimal(Double.toString(value1));

        BigDecimal b2 = new BigDecimal(Double.toString(value2));

        return b1.divide(b2,2,BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static double avg(List<Double> list){

        double avg = list.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
        return new BigDecimal(avg).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();

    }

    public static double keepTwoDecimal(double value){
        double twoDecimalValue = new BigDecimal(value).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        return twoDecimalValue;
    }
    /**
     * 平滑滤波
     * @param list
     * @param start
     * @param end
     * @param window
     * @return
     */
    public static List<RRIntervalPoint> smoothNew(List<RRIntervalPoint> list, int start,int end, int window) {

        if (CollectionUtil.isEmpty(list) || start > end || list.size() <= start || list.size() <= end) {
            throw new RuntimeException("参数错误");
        }

        List<RRIntervalPoint> listNew = new ArrayList<>();
        RRIntervalPoint rrData = new RRIntervalPoint();
        rrData.setHeartRate(new BigDecimal(list.get(0).getHeartRate()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
        rrData.setRrInterval(list.get(0).getRrInterval());
        rrData.setPosition(list.get(0).getPosition());
        listNew.add(rrData);
        for (int i = start + 1; i < end; i++) {
            double avg = 0.0;
            if (i-start < window/2) {
                int step = i-start;
                avg = list.stream().skip(start).limit(2 * step + 1).mapToDouble(RRIntervalPoint::getHeartRate).sum() / (2 * step + 1);
            } else if (end - i < window/2){
                int step = end - i;
                avg = list.stream().skip(i - step).limit(2*step + 1).mapToDouble(RRIntervalPoint::getHeartRate).sum() / (2 * step + 1);
            } else {
                avg = list.stream().skip(i - window/2).limit(window).mapToDouble(RRIntervalPoint::getHeartRate).sum() / window;
            }
            RRIntervalPoint rrData1 = new RRIntervalPoint();
            rrData1.setHeartRate(new BigDecimal(avg).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue());
            rrData1.setRrInterval(list.get(i).getRrInterval());
            rrData1.setPosition(list.get(i).getPosition());
            listNew.add(rrData1);
        }
        RRIntervalPoint rrDataEnd = new RRIntervalPoint();
        rrDataEnd.setHeartRate(list.get(list.size()-1).getHeartRate());
        rrDataEnd.setRrInterval(list.get(list.size()-1).getRrInterval());
        rrDataEnd.setPosition(list.get(list.size()-1).getPosition());
        listNew.add(rrDataEnd);
        return listNew;
    }

    public static double mean1(List<RRIntervalPoint> rrListSub) {

        double hrAvg = rrListSub.stream().mapToDouble(RRIntervalPoint::getHeartRate).average().getAsDouble();
        return hrAvg;
    }

    public static double randomDouble(double start,double end){
        double result = new BigDecimal(Math.random() * (end - start) + start).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }
    /**
     * 计算应激强度上包线值，下包线值，标准包线值
     * @param fclpNum
     * @return
     */
    public static List<Double[]> calculationEnvelope(int fclpNum) {

        double e = Math.E;

        //标准曲线
        double standardA = 1604.7881;
        double standardB = -0.025165;
        double standardC = 6311.2841;
        double standardD = -0.00016371;
        double standardY = 0.0;
        standardY =  standardA * Math.pow(e,standardB * fclpNum) + standardC * Math.pow(e,standardD * fclpNum);

        //上限包线
        double upperA = 2172.0256;
        double upperB = -0.045386;
        double upperC = 8628.093;
        double upperD = -0.0003001;
        double upperY = 0.0;
        upperY =  upperA * Math.pow(e,upperB * fclpNum) + upperC * Math.pow(e,upperD * fclpNum);

        //下限包线
        double lowerA = 1159.2422;
        double lowerB = -0.021275;
        double lowerC = 4233.2265;
        double lowerD = -5e-05;
        double lowerY = 0.0;
        lowerY =  lowerA * Math.pow(e,lowerB * fclpNum) + lowerC * Math.pow(e,lowerD * fclpNum);

        List<Double[]> list = new ArrayList<>();
        list.add(new Double[]{standardY,upperY,lowerY});
        return list;
    }

}