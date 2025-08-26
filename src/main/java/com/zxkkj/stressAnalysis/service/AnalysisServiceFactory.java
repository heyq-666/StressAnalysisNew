package com.zxkkj.stressAnalysis.service;

import com.zxkkj.stressAnalysis.constants.Constants;
import com.zxkkj.stressAnalysis.service.impl.AutomaticAnalysisServiceImpl;
import com.zxkkj.stressAnalysis.service.impl.ManualAnalysisServiceImpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AnalysisServiceFactory {
    private static final Map<Constants.AnalysisType, IAnalysisService> SERVICES;
    static {
        Map<Constants.AnalysisType, IAnalysisService> tempMap = new HashMap<>();
        tempMap.put(Constants.AnalysisType.automatic, new AutomaticAnalysisServiceImpl());
        tempMap.put(Constants.AnalysisType.manual, new ManualAnalysisServiceImpl());
        SERVICES = Collections.unmodifiableMap(tempMap);
    }

    public static Optional<IAnalysisService> getService(Constants.AnalysisType type) {
        return Optional.ofNullable(SERVICES.get(type));
    }
}