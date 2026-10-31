package dev.termsofsurrender.analysis;

import java.util.function.Consumer;

public interface AnalysisEngine {
    Analysis.Report analyze(Analysis.Input input);

    default Analysis.Report analyze(Analysis.Input input, Consumer<Analysis.Progress> onProgress) {
        return analyze(input);
    }

    default String progressStage() {
        return "Reviewing…";
    }
}
