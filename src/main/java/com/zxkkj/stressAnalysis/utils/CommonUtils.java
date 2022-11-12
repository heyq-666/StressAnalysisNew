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
            //throw new IllegalArgumentException("Number array must not empty !");
            return 0;
        }else {
            Integer maxValue = list.stream().max(Comparator.comparing(Integer::intValue)).get();
            return maxValue;
        }
    }

    public static Integer calculateMinValueInteger(List<Integer> list) {

        if (CollectionUtil.isEmpty(list)){
            //throw new IllegalArgumentException("Number array must not empty !");
            return 0;
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

    public static List<PeakModel> findPeakListNew(List<Double> zxxEcgList,double minpeakheight) {

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
                        if (zxxEcgList.get(i) >= minpeakheight){
                            PeakModel peakModel = new PeakModel();
                            peakModel.setPeakValue(zxxEcgList.get(i));
                            peakModel.setIndex(i);
                            peakList.add(peakModel);
                        }
                    }
                    flag = 1;
                }
            }
            return peakList;
        }
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

    /**
     * 计算波峰及位置
     * @param ecgList
     * @param minpeakheight
     * @param minpeakdistance
     * @return
     */
    private List<PeakModel> calPeakAndPosition(List<Double> ecgList,double minpeakheight,int minpeakdistance){

        if (CollectionUtil.isEmpty(ecgList)){
            throw new IllegalArgumentException("Number array must not empty !");
        }else {
            int flag = 0;
            List<PeakModel> peakList = new ArrayList<>();
            for (int i = 0; i < ecgList.size() - 1;) {//只记录波峰及位置
                if (ecgList.get(i + 1).compareTo(ecgList.get(i)) > 0){
                    flag = 2;
                }else if (ecgList.get(i + 1).compareTo(ecgList.get(i)) < 0){
                    if (flag == 2){
                        if (ecgList.get(i).compareTo(minpeakheight) >= 0){
                            PeakModel peakModel = new PeakModel();
                            peakModel.setIndex(i);
                            peakModel.setPeakValue(ecgList.get(i));
                            peakList.add(peakModel);
                            i += minpeakdistance;
                            continue;
                        }
                    }
                    flag = 1;
                }
                i++;
            }
            return peakList;
        }
    }
}