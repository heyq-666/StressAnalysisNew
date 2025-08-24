package com.zxkkj.stressAnalysis.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.zxkkj.stressAnalysis.RRIntervalCalculator;
import com.zxkkj.stressAnalysis.model.PeakModel;
import com.zxkkj.stressAnalysis.model.RRData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    public static List<RRIntervalCalculator.RRIntervalPoint> smoothNew1(List<RRIntervalCalculator.RRIntervalPoint> list, int start,int end, int window) {

        if (CollectionUtil.isEmpty(list) || start > end || list.size() <= start || list.size() <= end) {
            throw new RuntimeException("参数错误");
        }

        List<RRIntervalCalculator.RRIntervalPoint> listNew = new ArrayList<>();
        RRIntervalCalculator.RRIntervalPoint rrData = new RRIntervalCalculator.RRIntervalPoint(
                new BigDecimal(list.get(0).getHeartRate()).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue(),
                list.get(0).getHeartRate(),
                list.get(0).getPosition());
        listNew.add(rrData);
        for (int i = start + 1; i < end; i++) {
            double avg = 0.0;
            if (i-start < window/2) {
                int step = i-start;
                avg = list.stream().skip(start).limit(2 * step + 1).mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).sum() / (2 * step + 1);
            } else if (end - i < window/2){
                int step = end - i;
                avg = list.stream().skip(i - step).limit(2*step + 1).mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).sum() / (2 * step + 1);
            } else {
                avg = list.stream().skip(i - window/2).limit(window).mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).sum() / window;
            }
            RRIntervalCalculator.RRIntervalPoint rrData1 = new RRIntervalCalculator.RRIntervalPoint(
                    new BigDecimal(avg).setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue(),
                    list.get(i).getRrInterval(),
                    list.get(i).getPosition());
            listNew.add(rrData1);
        }
        RRIntervalCalculator.RRIntervalPoint rrDataEnd = new RRIntervalCalculator.RRIntervalPoint(
                list.get(list.size()-1).getHeartRate(),
                list.get(list.size()-1).getRrInterval(),
                list.get(list.size()-1).getPosition());

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

    public static double mean1(List<RRIntervalCalculator.RRIntervalPoint> rrListSub) {

        double hrAvg = rrListSub.stream().mapToDouble(RRIntervalCalculator.RRIntervalPoint::getHeartRate).average().getAsDouble();
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

    /**
     * 计算数组绝对值的最大值
     * @param array
     * @return
     */
    public static double findMaxAbsolute(double[] array) {
        double max = 0;
        for (double value : array) {
            double absValue = Math.abs(value);
            if (absValue > max) {
                max = absValue;
            }
        }
        return max;
    }

    /**
     * 找到第一个达到最大值的索引
     * @param array
     * @param maxValue
     * @return
     */
    public static int findFirstMaxIndex(double[] array, double maxValue) {
        for (int i = 0; i < array.length; i++) {
            if (Math.abs(array[i]) >= maxValue) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 在指定范围内查找最大绝对值
     * @param array
     * @param start
     * @param end
     * @return
     */
    public static double findMaxInRange(double[] array, int start, int end) {
        double max = 0;
        int low = Math.max(0, Math.min(start, end));
        int high = Math.min(array.length - 1, Math.max(start, end));

        for (int i = low; i <= high; i++) {
            double absValue = Math.abs(array[i]);
            if (absValue > max) {
                max = absValue;
            }
        }
        return max;
    }

    /**
     * 计算数组均值（忽略零值）
     * @param array
     * @return
     */
    public static double computeArrayMean(double[] array) {
        double sum = 0;
        int count = 0;

        for (double value : array) {
            // 忽略被置零的点
            if (value != 0) {
                sum += value;
                count++;
            }
        }

        return count > 0 ? sum / count : 0;
    }

    /**
     * FCLP段识别
     * @param hrList
     */
    public static List<Integer[]> fclpIdentity(List<RRIntervalCalculator.RRIntervalPoint> hrList) {

        //各fclp段数据
        List<Integer[]> fclpList = new ArrayList<>();
        int startHrIndex = 1;
        int segmentNew1=2000;
        int segmentNew0=100;
        while (startHrIndex < hrList.size()){
            if (startHrIndex + segmentNew1 - 1 < hrList.size()){
                List<RRIntervalCalculator.RRIntervalPoint> hrListSub = hrList.subList(startHrIndex,(startHrIndex + segmentNew1));
                List<RRIntervalCalculator.RRIntervalPoint> hrListSmooth = CommonUtils.smoothNew1(hrListSub,0,hrListSub.size() - 1,100);
                double means = CommonUtils.mean1(hrListSmooth);
                List<Double> hrListEnd = hrListSmooth.stream().map(item -> {
                    double hr = CommonUtils.keepTwoDecimal(item.getHeartRate() - means);
                    return hr;
                }).collect(Collectors.toList());
                //获取心率过0点位置
                List<Integer> passZeroHrNumList = new ArrayList<>();
                for (int i = 0; i < hrListEnd.size() - 1; i++) {
                    if (hrListEnd.get(i) * hrListEnd.get(i+1) < 0){
                        passZeroHrNumList.add(i);
                    }
                }
                //过0点心率的间隔
                List<Integer> passZeroIntervalNum = new ArrayList<>();
                for (int i = 0; i < passZeroHrNumList.size() - 1; i++) {
                    passZeroIntervalNum.add(passZeroHrNumList.get(i + 1) - passZeroHrNumList.get(i));
                }
                int passZeroMin = CommonUtils.calculateMinValueInteger(passZeroIntervalNum);
                int passZeroMax = CommonUtils.calculateMaxValueInteger(passZeroIntervalNum);
                int sub = passZeroMax - passZeroMin;
                if (passZeroHrNumList.size() > 2 && passZeroMin > 105 && sub < 200){
                    fclpList.add(new Integer[]{startHrIndex,startHrIndex + segmentNew1 - 1});
                    startHrIndex += segmentNew1;
                }else {
                    startHrIndex += segmentNew0;
                }
            }else {//若不够一个判断长度，则按segment0心率长度进行判断
                if (hrList.size() - startHrIndex > segmentNew0){
                    List<RRIntervalCalculator.RRIntervalPoint> hrListSub = hrList.subList(startHrIndex,(startHrIndex + segmentNew0 - 1));
                    List<RRIntervalCalculator.RRIntervalPoint> hrListSmooth = CommonUtils.smoothNew1(hrListSub,0,hrListSub.size() - 1,100);
                    double means = CommonUtils.mean1(hrListSmooth);
                    List<Double> hrListEnd = hrListSmooth.stream().map(item -> {
                        double hr = CommonUtils.keepTwoDecimal(item.getHeartRate() - means);
                        return hr;
                    }).collect(Collectors.toList());
                    //获取心率过0点位置
                    List<Integer> passZeroHrNumList = new ArrayList<>();
                    for (int i = 0; i < hrListEnd.size() - 1; i++) {
                        if (hrListEnd.get(i) * hrListEnd.get(i+1) < 0){
                            passZeroHrNumList.add(i);
                        }
                    }
                    //过0点心率的间隔
                    List<Integer> passZeroIntervalNum = new ArrayList<>();
                    for (int i = 0; i < passZeroHrNumList.size() - 1; i++) {
                        passZeroIntervalNum.add(passZeroHrNumList.get(i + 1) - passZeroHrNumList.get(i));
                    }
                    int passZeroMin = CommonUtils.calculateMinValueInteger(passZeroIntervalNum);
                    int passZeroMax = CommonUtils.calculateMaxValueInteger(passZeroIntervalNum);
                    int sub = passZeroMax - passZeroMin;
                    if (passZeroHrNumList.size() > 2 && passZeroMin > 105 && sub < 200){
                        fclpList.add(new Integer[]{startHrIndex,startHrIndex + segmentNew0 - 1});
                    }
                    startHrIndex = hrList.size();
                }else {
                    startHrIndex = hrList.size();
                }
            }
        }
        return fclpList;
    }
}