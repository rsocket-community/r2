package com.github.mostroverkhov.r2.java;

import com.github.mostroverkhov.r2.core.internal.responder.RequestAcceptor;
import com.github.mostroverkhov.r2.core.internal.responder.RequestAcceptorBuilder;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.function.Function;

public class JavaAcceptorBuilder extends RequestAcceptorBuilder<ConnectionSetupPayload, Mono<RSocket>> {

    @NotNull
    @Override
    public JavaRequestAcceptor build() {
        return new JavaRequestAcceptor(metadata ->
                new JavaRequestHandler(targetResolver(metadata)));
    }

    static class JavaRequestAcceptor implements RequestAcceptor<ConnectionSetupPayload, Mono<RSocket>> {

        private final Function<ByteBuffer, RSocket> handlerFactory;

        public JavaRequestAcceptor(Function<ByteBuffer, RSocket> handlerFactory) {
            this.handlerFactory = handlerFactory;
        }

        @Override
        public Mono<RSocket> accept(ConnectionSetupPayload setup) {
            return Mono.just(handlerFactory.apply(setup.getMetadata()));
        }
    }
}