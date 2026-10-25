package dev.termsofsurrender.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.analysis.AnalysisEngine;
import dev.termsofsurrender.documents.DocumentExtractor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisWorkerTest {
    private final AnalysisRepository repository = mock(AnalysisRepository.class);
    private final AnalysisEngine engine = mock(AnalysisEngine.class);

    @Test
    void disabledWorkerNeverClaimsWork() {
        new AnalysisWorker(repository, engine, config(false)).processNext();
        verifyNoInteractions(repository, engine);
    }

    @Test
    void providerFailurePersistsAFailureInsteadOfAPreviewReport() {
        var input =
                new Analysis.Input(
                        new DocumentExtractor()
                                .extract(
                                        "This agreement renews automatically every year unless written notice is received thirty days before renewal.",
                                        null),
                        new Analysis.Context(
                                Analysis.Jurisdiction.INDIA, Analysis.Category.FREELANCE, ""));
        UUID id = UUID.randomUUID();
        when(repository.claim()).thenReturn(Optional.of(new AnalysisRepository.Job(id, input)));
        when(engine.analyze(input))
                .thenThrow(new IllegalStateException("Private provider details must not escape"));
        new AnalysisWorker(repository, engine, config(true)).processNext();
        verify(repository).fail(id);
        verify(repository, never()).complete(any(), any());
    }

    @Test
    void anEmptyQueueDoesNotInvokeTheProvider() {
        when(repository.claim()).thenReturn(Optional.empty());
        new AnalysisWorker(repository, engine, config(true)).processNext();
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
                new AppProperties.Model(false, "https://example.com", "", ""));
    }
}
