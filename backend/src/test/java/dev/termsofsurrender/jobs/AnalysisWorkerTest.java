package dev.termsofsurrender.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.analysis.AnalysisEngine;
import dev.termsofsurrender.analysis.PreviewAnalysisEngine;
import dev.termsofsurrender.documents.DocumentExtractor;
import dev.termsofsurrender.privacy.AiDemoQuota;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalysisWorkerTest {
    private final AnalysisRepository repository = mock(AnalysisRepository.class);
    private final AnalysisEngine engine = mock(AnalysisEngine.class);
    private final PreviewAnalysisEngine preview = mock(PreviewAnalysisEngine.class);
    private final AiDemoQuota quota = mock(AiDemoQuota.class);

    @BeforeEach
    void setup() {
        when(quota.start(any(), anyBoolean())).thenReturn(true);
    }

    private AnalysisWorker worker(boolean enabled) {
        return new AnalysisWorker(repository, engine, config(enabled), preview, quota);
    }

    @Test
    void disabledWorkerNeverClaimsWork() {
        worker(false).processNext();
        verifyNoInteractions(repository, engine);
    }

    @Test
    void providerFailurePublishesAnExplicitPreviewFallback() {
        var input =
                new Analysis.Input(
                        new DocumentExtractor()
                                .extract(
                                        "This agreement renews automatically every year unless written notice is received thirty days before renewal.",
                                        null),
                        new Analysis.Context(
                                Analysis.Jurisdiction.INDIA, Analysis.Category.FREELANCE, ""));
        UUID id = UUID.randomUUID();
        when(repository.claim())
                .thenReturn(
                        Optional.of(
                                new AnalysisRepository.Job(
                                        id, input, Analysis.Mode.MODEL_ASSISTED)));
        when(engine.analyze(eq(input), any()))
                .thenThrow(new IllegalStateException("Private provider details must not escape"));
        var report = mock(Analysis.Report.class);
        when(preview.analyze(input)).thenReturn(report);
        worker(true).processNext();
        verify(repository).completePreview(id, report, "AI_UNAVAILABLE");
        verify(repository, never()).fail(id);
        verify(repository, never()).complete(any(), any());
    }

    @Test
    void aTransientProgressFailureDoesNotInterruptFinalReportPersistence() {
        UUID id = UUID.randomUUID();
        var input = mock(Analysis.Input.class);
        var report = mock(Analysis.Report.class);
        when(repository.claim())
                .thenReturn(
                        Optional.of(
                                new AnalysisRepository.Job(
                                        id, input, Analysis.Mode.MODEL_ASSISTED)));
        doThrow(new IllegalStateException("Progress storage temporarily unavailable"))
                .when(repository)
                .updateProgress(eq(id), any());
        when(engine.analyze(eq(input), any()))
                .thenAnswer(
                        invocation -> {
                            Consumer<Analysis.Progress> callback = invocation.getArgument(1);
                            callback.accept(new Analysis.Progress(0, 1, List.of()));
                            return report;
                        });
        worker(true).processNext();
        verify(repository).complete(id, report);
        verify(repository, never()).fail(any());
    }

    @Test
    void anEmptyQueueDoesNotInvokeTheProvider() {
        when(repository.claim()).thenReturn(Optional.empty());
        worker(true).processNext();
        verifyNoInteractions(engine);
    }

    private AppProperties config(boolean enabled) {
        return new AppProperties(
                List.of("http://localhost:3000"),
                false,
                24,
                2,
                10,
                50,
                enabled,
                new AppProperties.Model(true, "https://example.com", "test", "test"));
    }
}
