package com.github.mostroverkhov.r2.android

import com.github.mostroverkhov.r2.core.*
import com.github.mostroverkhov.r2.core.internal.requester.metaData
import io.reactivex.Single
import io.rsocket.android.RSocketFactory.ClientRSocketFactory
import io.rsocket.android.transport.ClientTransport
import io.rsocket.android.util.PayloadImpl

typealias FluentBuilder = R2ClientFluentBuilder<
        ClientRSocketFactory,
        ClientAcceptorBuilder,
        ClientTransport,
        Single<RequesterFactory>>

typealias AcceptorConfigurer = (ClientAcceptorBuilder) -> ClientAcceptorBuilder

class R2Client : FluentBuilder() {
    private var configurer: AcceptorConfigurer? = null
    private var metadata: Metadata? = null
    private var clientTransport: ClientTransport? = null

    override fun metadata(metadata: Metadata): FluentBuilder {
        this.metadata = metadata
        return this
    }

    override fun transport(transport: ClientTransport): FluentBuilder {
        clientTransport = transport
        return this
    }

    override fun configureAcceptor(f: AcceptorConfigurer): FluentBuilder {
        configurer = f
        return this
    }

    override fun start(): Single<RequesterFactory> {
        assertState()
        val acceptorBuilder = ClientAcceptorBuilder()
        val transport = clientTransport!!
        val configure = configurer!!

        val configuredAcceptorBuilder = configure(acceptorBuilder)
        val clientAcceptor = configuredAcceptorBuilder.build()
        val requesterCodec = configuredAcceptorBuilder.codecs().primary()

        val rSocket = withSetup(rSocketFactory)
                .acceptor { clientAcceptor::accept }
                .transport(transport)
                .start()

        return rSocket.map(::RequesterBuilder)
                .map { it.codec(requesterCodec) }
                .map(CoreRequesterBuilder::build)
    }

    private fun assertState() {
        assertArg(clientTransport, "ClientRSocketFactory")
        assertArg(clientTransport, "ClientTransport")
        assertArg(configurer, "RequesterConfigurer")
    }

    private fun assertArg(arg: Any?, name: String) {
        if (arg == null) {
            throw IllegalArgumentException("$name was not set")
        }
    }

    private fun withSetup(factory: ClientRSocketFactory?): ClientRSocketFactory {
        val setupData = metaData(metadata)
        return factory
                ?.dataMimeType(setupData.dataType)
                ?.metadataMimeType(setupData.metadataType)
                ?.setupPayload(
                        PayloadImpl(
                                setupData.data,
                                setupData.metadata)
                )
                ?: throw IllegalArgumentException("ClientRSocketFactory not set")
    }
}