package dev.termsofsurrender.api;

import dev.termsofsurrender.analysis.Analysis;
import dev.termsofsurrender.jobs.AnalysisRepository;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AnalysisStreams {
    private final ConcurrentHashMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AnalysisRepository repository;

    public AnalysisStreams(AnalysisRepository repository) {
        this.repository = repository;
    }

    public synchronized SseEmitter open(UUID analysisId, String owner) {
        repository.find(analysisId, owner).orElseThrow(ApiException::notFound);
        long ownStreams =
                subscriptions.values().stream().filter(value -> value.owner.equals(owner)).count();
        if (subscriptions.size() >= 100 || ownStreams >= 3) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "STREAM_LIMIT",
                    "Too many report connections. Close another tab and retry.");
        }
        SseEmitter emitter = new SseEmitter(110_000L);
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = new Subscription(analysisId, owner, emitter);
        subscriptions.put(subscriptionId, subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscriptionId));
        emitter.onTimeout(
                () -> {
                    subscriptions.remove(subscriptionId);
                    emitter.complete();
                });
        emitter.onError(error -> subscriptions.remove(subscriptionId));
        return emitter;
    }

    @Scheduled(fixedDelay = 1_000)
    public void publishSnapshots() {
        subscriptions.forEach(
                (id, subscription) -> {
                    try {
                        var snapshot = repository.find(subscription.analysisId, subscription.owner);
                        if (snapshot.isEmpty()) {
                            subscription.emitter.send(
                                    SseEmitter.event()
                                            .name("unavailable")
                                            .data("Report deleted or expired"));
                            finish(id, subscription);
                            return;
                        }
                        Analysis.View view = snapshot.get();
                        if (view.version() != subscription.lastVersion) {
                            subscription.emitter.send(
                                    SseEmitter.event()
                                            .name("snapshot")
                                            .id(Integer.toString(view.version()))
                                            .data(view));
                            subscription.lastVersion = view.version();
                        } else if (++subscription.ticks % 15 == 0) {
                            subscription.emitter.send(SseEmitter.event().comment("keep-alive"));
                        }
                        if (view.status() == Analysis.Status.COMPLETED
                                || view.status() == Analysis.Status.FAILED) {
                            finish(id, subscription);
                        }
                    } catch (IOException | IllegalStateException exception) {
                        finish(id, subscription);
                    }
                });
    }

    private void finish(UUID id, Subscription subscription) {
        subscriptions.remove(id);
        subscription.emitter.complete();
    }

    private static final class Subscription {
        private final UUID analysisId;
        private final String owner;
        private final SseEmitter emitter;
        private int lastVersion = -1;
        private int ticks;

        private Subscription(UUID analysisId, String owner, SseEmitter emitter) {
            this.analysisId = analysisId;
            this.owner = owner;
            this.emitter = emitter;
        }
    }
}
