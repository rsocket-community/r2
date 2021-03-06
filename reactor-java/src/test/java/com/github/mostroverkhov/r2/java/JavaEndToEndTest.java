package com.github.mostroverkhov.r2.java;

import com.github.mostroverkhov.r2.codec.jackson.JacksonJsonDataCodec;
import com.github.mostroverkhov.r2.core.Codecs;
import com.github.mostroverkhov.r2.core.Metadata;
import com.github.mostroverkhov.r2.core.RequesterFactory;
import com.github.mostroverkhov.r2.core.Services;
import com.github.mostroverkhov.r2.core.internal.MetadataCodec;
import com.github.mostroverkhov.r2.reactor.internal.RequesterBuilder;
import com.github.mostroverkhov.r2.reactor.ServerAcceptorBuilder;
import com.github.mostroverkhov.r2.reactor.InteractionsInterceptor;
import io.rsocket.AbstractRSocket;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Frame;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static com.github.mostroverkhov.r2.java.JavaMocks.*;
import static org.junit.Assert.assertEquals;

public class JavaEndToEndTest {

    private PersonsService personsService;

    @Before
    public void setUp() throws Exception {
        List<InteractionsInterceptor> empty = Collections.emptyList();
        Mono<RSocket> handlerRSocket = new ServerAcceptorBuilder(empty, empty)
                .codecs(new Codecs().add(new JacksonJsonDataCodec()))
                .services((ctx, requesterFactory) ->
                    new Services()
                        .add(new PersonServiceHandler("")))
                .build()
                .accept(mockSetupPayload(), mockRSocket());

      RequesterFactory requesterFactory = handlerRSocket
                .map(rs ->
                        new RequesterBuilder(rs, empty)
                                .codec(new JacksonJsonDataCodec())
                                .build())
                .block();

        personsService = requesterFactory.create(PersonsService.class);
    }

    @Test(timeout = 5_000)
    public void stream() throws Exception {
        Person expected = expectedPerson();
        List<Person> list = personsService.stream(expected)
                .collectList().block();
        assertEquals(1, list.size());
        Person actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test(timeout = 5_000)
    public void fireAndForget() throws Exception {
        Metadata md = new Metadata.Builder()
                .data("foo", "bar".getBytes(Charsets.UTF_8))
                .build();
        personsService.fnf(expectedPerson(), md).block();
    }

    @Test(timeout = 5_000)
    public void response() throws Exception {
        Metadata md = new Metadata.Builder()
                .data("foo", "bar".getBytes(Charsets.UTF_8))
                .build();
        Person expected = expectedPerson();
        Person actual = personsService.response(expected, md).block();
        assertEquals(expected, actual);
    }

    @Test(timeout = 5_000)
    public void channel() throws Exception {
        Person expected = expectedPerson();
        List<Person> list = personsService
            .channel(Flux.just(expected)).collectList().block();
        assertEquals(2, list.size());
        Person actual = list.get(0);
        assertEquals(expected, actual);
    }

    @Test(timeout = 5_000)
    public void emptyResponse() throws Exception {
        Person actual = personsService.responseEmpty().block();
        assertEquals(expectedPerson(), actual);
    }

    @Test(timeout = 5_000)
    public void onlyMetadataResponse() throws Exception {
        Metadata md = new Metadata.Builder()
                .data("foo", "bar".getBytes(Charsets.UTF_8))
                .build();

        Person actual = personsService.responseMetadata(md).block();
        assertEquals(expectedPerson(), actual);
    }

    @Test(timeout = 5_000, expected = IllegalArgumentException.class)
    public void noAnno() throws Exception {
        personsService.noAnno(expectedPerson()).collectList().block();
    }

    @Test(timeout = 5_000, expected = IllegalArgumentException.class)
    public void emptyAnno() {
        personsService.emptyAnno(expectedPerson()).collectList().block();
    }

    @NotNull
    private ConnectionSetupPayload mockSetupPayload() {
        Metadata md = new Metadata.Builder()
                .data("auth", Charsets.UTF_8.encode("secret"))
                .build();
        ByteBuffer encodedMd = new MetadataCodec().encode(md);
        Frame mockSetupFrame = Frame.Setup.from(0,
          1,
          3,
          "stub",
          "stub",
          DefaultPayload.create(ByteBuffer.allocate(0),
              encodedMd));
      return ConnectionSetupPayload
                .create(mockSetupFrame);
    }

    @NotNull
    private RSocket mockRSocket() {
        return new AbstractRSocket() {
        };
    }

    @NotNull
    private Person expectedPerson() {
        return new Person("john", "doe");
    }
}
