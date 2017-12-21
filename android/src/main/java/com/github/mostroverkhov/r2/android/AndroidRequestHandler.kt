package com.github.mostroverkhov.r2.android

import com.github.mostroverkhov.r2.core.internal.responder.ResponderTargetResolver
import com.github.mostroverkhov.r2.core.internal.responder.TargetAction
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.AbstractRSocket
import io.rsocket.android.Payload
import io.rsocket.android.util.PayloadImpl
import org.reactivestreams.Publisher
import java.nio.ByteBuffer

class AndroidRequestHandler(private val targetResolver: ResponderTargetResolver) : AbstractRSocket() {

    override fun fireAndForget(payload: Payload) = callFireAndForget(payload)

    override fun requestChannel(payloads: Publisher<Payload>) = callRequestChannel(payloads)

    override fun requestResponse(payload: Payload) = callRequestResponse(payload)

    override fun requestStream(payload: Payload) = callRequestStream(payload)

    private fun callFireAndForget(arg: Payload): Completable {
        val targetAction = targetResolver.resolveTarget(arg)
        return targetAction()
    }

    private fun callRequestResponse(arg: Payload): Single<Payload> {
        val targetAction = targetResolver.resolveTarget(arg)
        return targetAction<Single<*>>()
                .map { targetAction.encode(it) }
                .map { asPayload(it) }
    }

    private fun callRequestStream(arg: Payload): Flowable<Payload> {
        val targetAction = targetResolver.resolveTarget(arg)
        return targetAction<Flowable<*>>()
                .map { targetAction.encode(it) }
                .map { asPayload(it) }
    }

    private fun callRequestChannel(arg: Publisher<Payload>): Flowable<Payload> {
        return split(arg)
                .flatMap { headTail ->
                    val (headPayload, tailPayload) = headTail
                    val targetAction = targetResolver.resolveTarget(headPayload)
                    val payloadT = tailPayload
                            .startWith(headPayload)
                            .map { targetAction.decode(it.data) }
                    targetAction<Flowable<*>>(payloadT)
                            .map { targetAction.encode(it) }
                            .map { asPayload(it) }
                }
    }

    private fun ResponderTargetResolver.resolveTarget(payload: Payload): TargetAction
            = resolve(payload.data, payload.metadata)

    companion object {

        private fun asPayload(data: ByteBuffer): Payload
                = PayloadImpl(data, null)

        private fun split(p: Publisher<Payload>): Flowable<Split> {
            var first = true
            val rest = UnicastProcessor.create<Payload>()
            val channelArg = UnicastProcessor.create<Split>()

            return Flowable.fromPublisher(p)
                    .doOnComplete { rest.onComplete() }
                    .doOnError { rest.onError(it) }
                    .flatMap { payload ->
                        if (first) {
                            first = false
                            channelArg.onNext(Split(payload, rest))
                            channelArg.onComplete()
                            channelArg
                        } else {
                            rest.onNext(payload)
                            Flowable.empty<Split>()
                        }
                    }
        }
    }

    private data class Split(val head: Payload, val tail: Flowable<Payload>)
}