package dev.termsofsurrender.jobs;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.AnalysisEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalysisWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisWorker.class);
    private final AnalysisRepository repository;
    private final AnalysisEngine engine;
    private final AppProperties properties;

    public AnalysisWorker(
            AnalysisRepository repository, AnalysisEngine engine, AppProperties properties) {
        this.repository = repository;
        this.engine = engine;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.worker-delay-ms:750}")
    public void processNext() {
        if (!properties.workerEnabled()) {
            return;
        }
        repository
                .claim()
                .ifPresent(
                        job -> {
                            try {
                                repository.complete(job.id(), engine.analyze(job.input()));
                            } catch (Exception exception) {
                                LOG.warn(
                                        "Analysis {} failed with {}",
                                        job.id(),
                                        exception.getClass().getSimpleName());
                                repository.fail(job.id());
                            }
                        });
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireData() {
        repository.cleanup();
    }
}
