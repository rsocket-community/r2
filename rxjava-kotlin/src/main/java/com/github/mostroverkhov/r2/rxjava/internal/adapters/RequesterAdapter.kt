package com.github.mostroverkhov.r2.rxjava.internal.adapters

import com.github.mostroverkhov.r2.core.internal.requester.*
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import org.reactivestreams.Publisher
import java.lang.reflect.Method

internal class RequesterAdapter(private val rSocket: RSocket) : CallAdapter {

    override fun adapt(call: Call): Any {

        return when (call.interaction) {

            Interaction.CHANNEL -> Flowable.defer {
                rSocket.requestChannel(call.encodePublisher())
                        .map { call.decode(it) }
            }

            Interaction.RESPONSE -> Single.defer {
                rSocket.requestResponse(call.encode())
                        .map { call.decode(it) }
            }

            Interaction.STREAM -> Flowable.defer {
                rSocket.requestStream(call.encode())
                        .map { call.decode(it) }
            }

            Interaction.FNF -> Completable.defer {
                rSocket.fireAndForget(call.encode())
            }

            Interaction.CLOSE -> rSocket.close()

            Interaction.ONCLOSE -> rSocket.onClose()
        }
    }

    override fun resolve(action: Method, err: RuntimeException): Any =
            with(action.returnType) {
                when {
                    typeIs<Completable>() -> Completable.error(err)
                    typeIs<Single<*>>() -> Single.error<Any>(err)
                    typeIs<Flowable<*>>() -> Flowable.error<Any>(err)
                    else -> throw err
                }
            }

    private inline fun <reified T> Class<*>.typeIs()
            = T::class.java == this

    private fun Call.decode(arg: Payload): Any {
        this as RequesterCall
        return decodeData(arg.data)
    }

    private fun Call.encode(): Payload {
        this as RequesterCall
        return DefaultPayload(encodeData(params().data), encodeMetadata())
    }

    private fun Call.encodePublisher(): Publisher<Payload> {
        this as RequesterCall
        var first = true
        return Flowable.fromPublisher(params().data as Publisher<*>)
                .map { t ->
                    val metadata = if (first) {
                        first = false
                        encodeMetadata()
                    } else null
                    DefaultPayload(encodeData(t), metadata)
                }
    }
}