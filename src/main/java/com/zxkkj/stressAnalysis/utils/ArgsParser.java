package com.zxkkj.stressAnalysis.utils;

import com.zxkkj.stressAnalysis.constants.Constants;

import java.util.Objects;

public class ArgsParser {
    private final String[] args;
    private final Constants.AnalysisType type;

    public ArgsParser(String[] args) {
        this.args = Objects.requireNonNull(args, "参数不能为null");
        this.type = Constants.AnalysisType.fromValue(args[0])
                .orElseThrow(() -> new IllegalArgumentException("无效的参数类型"));
        validate();
    }

    private void validate() {
        switch (type) {
            case automatic:
                if (args.length < 3) throw new IllegalArgumentException("自动模式需要至少3个参数");
                break;
            case manual:
                if (args.length < 5) throw new IllegalArgumentException("手动模式需要至少5个参数");
                break;
        }
    }

    public Constants.AnalysisType getType() { return type; }
    public String[] getArgs() { return args; }
}