package com.github.mostroverkhov.r2.java;

import com.github.mostroverkhov.r2.core.internal.responder.ResponderTargetResolver;
import com.github.mostroverkhov.r2.core.internal.responder.TargetAction;
import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.util.PayloadImpl;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaRequestHandler extends AbstractRSocket {
    private final ResponderTargetResolver targetResolver;

    public JavaRequestHandler(ResponderTargetResolver targetResolver) {
        this.targetResolver = targetResolver;
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        return callFireAndForget(payload);
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        return callRequestResponse(payload);
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        return callRequestStream(payload);
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        return callRequestChannel(payloads);
    }

    private Mono<Void> callFireAndForget(Payload payload) {
        TargetAction targetAction = resolveTarget(payload);
        return targetAction.invoke();
    }

    private Mono<Payload> callRequestResponse(Payload payload) {
        TargetAction targetAction = resolveTarget(payload);
        return targetAction.<Mono<?>>invoke()
                .map(targetAction::encode)
                .map(this::asPayload);
    }

    private Flux<Payload> callRequestStream(Payload payload) {
        TargetAction targetAction = resolveTarget(payload);
        return targetAction.<Flux<?>>invoke()
                .map(targetAction::encode)
                .map(this::asPayload);
    }

    private Flux<Payload> callRequestChannel(Publisher<Payload> arg) {
        return split(arg)
                .flatMap(headTail -> {
                    Payload headPayload = headTail.head();
                    Flux<Payload> tailPayload = headTail.tail();
                    TargetAction targetAction = resolveTarget(headPayload);
                    Flux<Object> payloadT = tailPayload
                            .startWith(headPayload)
                            .map(p -> targetAction.decode(p.getData()));
                    return targetAction.<Flux<?>>invoke(payloadT)
                            .map(targetAction::encode)
                            .map(this::asPayload);
                });
    }

    private TargetAction resolveTarget(Payload payload) {
        return targetResolver.resolve(payload.getData(), payload.getMetadata());
    }

    private Payload asPayload(ByteBuffer data) {
        return new PayloadImpl(data, null);
    }

    private static Flux<Split> split(Publisher<Payload> p) {
        AtomicBoolean first = new AtomicBoolean(true);
        UnicastProcessor<Payload> rest = UnicastProcessor.create();
        UnicastProcessor<Split> channelArg = UnicastProcessor.create();

        return Flux.from(p)
                .doOnComplete(rest::onComplete)
                .doOnError(rest::onError)
                .flatMap(payload -> {
                    if (first.compareAndSet(true, false)) {
                        channelArg.onNext(new Split(payload, rest));
                        channelArg.onComplete();
                        return channelArg;
                    } else {
                        rest.onNext(payload);
                        return Flux.empty();
                    }
                });
    }

    private static class Split {
        private final Payload head;
        private final Flux<Payload> tail;

        public Split(Payload head, Flux<Payload> tail) {
            this.head = head;
            this.tail = tail;
        }

        public Payload head() {
            return head;
        }

        public Flux<Payload> tail() {
            return tail;
        }
    }
}
