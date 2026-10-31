package dev.termsofsurrender.jobs;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.analysis.AnalysisEngine;
import dev.termsofsurrender.analysis.PreviewAnalysisEngine;
import dev.termsofsurrender.privacy.AiDemoQuota;
import java.util.UUID;
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
    private final PreviewAnalysisEngine preview;
    private final AiDemoQuota quota;

    public AnalysisWorker(
            AnalysisRepository repository,
            AnalysisEngine engine,
            AppProperties properties,
            PreviewAnalysisEngine preview,
            AiDemoQuota quota) {
        this.repository = repository;
        this.engine = engine;
        this.properties = properties;
        this.preview = preview;
        this.quota = quota;
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
                            AnalysisEngine selected =
                                    job.mode() == Analysis.Mode.MODEL_ASSISTED
                                                    && properties.model().enabled()
                                            ? engine
                                            : preview;
                            try {
                                if (!quota.start(job.id(), selected != preview)) {
                                    return;
                                }
                                repository.updateStage(job.id(), selected.progressStage());
                                var report =
                                        selected.analyze(
                                                job.input(),
                                                progress -> publishProgress(job.id(), progress));
                                if (job.mode() == Analysis.Mode.MODEL_ASSISTED
                                        && selected == preview) {
                                    repository.completePreview(job.id(), report, "AI_DISABLED");
                                } else {
                                    repository.complete(job.id(), report);
                                }
                            } catch (Exception exception) {
                                LOG.warn(
                                        "Analysis {} failed with {}",
                                        job.id(),
                                        exception.getClass().getSimpleName());
                                if (selected != preview) {
                                    try {
                                        repository.completePreview(
                                                job.id(),
                                                preview.analyze(job.input()),
                                                "AI_UNAVAILABLE");
                                    } catch (Exception fallbackFailure) {
                                        repository.fail(job.id());
                                    }
                                } else {
                                    repository.fail(job.id());
                                }
                            }
                        });
    }

    private void publishProgress(UUID id, Analysis.Progress progress) {
        try {
            repository.updateProgress(id, progress);
        } catch (RuntimeException exception) {
            // Live feedback is an enhancement; a transient progress-write failure must not
            // interrupt the unchanged analysis or prevent final report persistence.
            LOG.warn(
                    "Analysis {} progress update unavailable ({})",
                    id,
                    exception.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireData() {
        repository.cleanup();
    }
}
