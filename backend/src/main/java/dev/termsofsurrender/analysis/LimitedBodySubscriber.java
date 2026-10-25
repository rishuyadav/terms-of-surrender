package dev.termsofsurrender.analysis;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bound provider responses before buffering them, including chunked HTTP responses. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate =
            HttpResponse.BodySubscribers.ofByteArray();
    private final int maximum;
    private Flow.Subscription subscription;
    private long received;
    private boolean failed;

    LimitedBodySubscriber(int maximum) {
        this.maximum = maximum;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription incoming) {
        subscription = incoming;
        delegate.onSubscribe(incoming);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (failed) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            received += buffer.remaining();
        }
        if (received > maximum) {
            failed = true;
            subscription.cancel();
            delegate.onError(new IllegalStateException("Model response exceeded limit"));
            return;
        }
        delegate.onNext(buffers);
    }

    @Override
    public void onError(Throwable error) {
        if (!failed) {
            failed = true;
            delegate.onError(error);
        }
    }

    @Override
    public void onComplete() {
        if (!failed) {
            delegate.onComplete();
        }
    }
}
