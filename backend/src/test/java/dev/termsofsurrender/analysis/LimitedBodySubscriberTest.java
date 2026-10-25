package dev.termsofsurrender.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class LimitedBodySubscriberTest {
    @Test
    void cancelsOversizeResponseBeforeBufferingIt() {
        var subscriber = new LimitedBodySubscriber(4);
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[5])));
        verify(subscription).cancel();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsBoundedResponse() {
        var subscriber = new LimitedBodySubscriber(4);
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {3, 4})));
        subscriber.onComplete();
        assertThat(subscriber.getBody().toCompletableFuture().join()).containsExactly(1, 2, 3, 4);
    }
}
