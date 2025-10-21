/*
 * MIT License
 *
 * Copyright (c) 2025 Andrey Karazhev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.akarazhev.cryptoscout.analyst;

import com.github.akarazhev.cryptoscout.analyst.db.StreamOffsetsRepository;
import com.github.akarazhev.jcryptolib.stream.Provider;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler;
import com.rabbitmq.stream.Producer;
import com.rabbitmq.stream.SubscriptionListener;
import io.activej.async.service.ReactiveService;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executor;

import com.github.akarazhev.cryptoscout.config.AmqpConfig;
import com.github.akarazhev.jcryptolib.stream.Payload;
import com.github.akarazhev.jcryptolib.util.JsonUtils;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.OffsetSpecification;

public final class AmqpClient extends AbstractReactive implements ReactiveService {
    private final static Logger LOGGER = LoggerFactory.getLogger(AmqpClient.class);
    private final Executor executor;
    private final StreamOffsetsRepository streamOffsetsRepository;
    private final CryptoBybitAnalyst cryptoBybitAnalyst;
    private volatile Environment environment;
    private volatile Consumer consumer;
    private volatile Producer producer;

    public static AmqpClient create(final NioReactor reactor, final Executor executor,
                                    final StreamOffsetsRepository streamOffsetsRepository,
                                    final CryptoBybitAnalyst cryptoBybitAnalyst) {
        return new AmqpClient(reactor, executor, streamOffsetsRepository, cryptoBybitAnalyst);
    }

    private AmqpClient(final NioReactor reactor, final Executor executor,
                       final StreamOffsetsRepository streamOffsetsRepository,
                       final CryptoBybitAnalyst cryptoBybitAnalyst) {
        super(reactor);
        this.executor = executor;
        this.streamOffsetsRepository = streamOffsetsRepository;
        this.cryptoBybitAnalyst = cryptoBybitAnalyst;
    }

    @Override
    public Promise<?> start() {
        return Promise.ofBlocking(executor, () -> {
            try {
                final var cryptoBybitStream = AmqpConfig.getAmqpCryptoBybitStream();
                final var cryptoBybitTaStream = AmqpConfig.getAmqpCryptoBybitTaStream();
                environment = AmqpConfig.getEnvironment();
                producer = environment.producerBuilder()
                        .name(cryptoBybitTaStream)
                        .stream(cryptoBybitTaStream)
                        .build();
                consumer = environment.consumerBuilder()
                        .stream(cryptoBybitStream)
                        .noTrackingStrategy() // todo: implement filter
                        .subscriptionListener(c -> updateOffset(cryptoBybitStream, c))
                        .messageHandler((c, m) -> consumePayload(cryptoBybitTaStream, c, m))
                        .build();
                LOGGER.info("AmqpClient started");
            } catch (final Exception ex) {
                LOGGER.error("Failed to start AmqpClient", ex);
                throw new RuntimeException(ex);
            }
        });
    }

    @Override
    public Promise<?> stop() {
        return Promise.ofBlocking(executor, () -> {
            closeConsumer(consumer);
            consumer = null;
            closeProducer(producer);
            producer = null;
            closeEnvironment();
            LOGGER.info("AmqpClient stopped");
        });
    }

    private void updateOffset(final String stream, final SubscriptionListener.SubscriptionContext context) {
        reactor.execute(() -> Promise.ofBlocking(executor, () -> streamOffsetsRepository.getOffset(stream))
                .then(saved -> {
                    if (saved.isPresent()) {
                        context.offsetSpecification(OffsetSpecification.offset(saved.getAsLong() + 1));
                        LOGGER.info("Consumer starting from DB offset {}+1 for stream {}", saved.getAsLong(), stream);
                    } else {
                        context.offsetSpecification(OffsetSpecification.first());
                        LOGGER.info("Consumer starting from first for stream {}", stream);
                    }

                    return Promise.complete();
                })
                .whenComplete((_, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("Failed to load offset from DB, starting from first", ex);
                        context.offsetSpecification(OffsetSpecification.first());
                    }
                })
        );
    }

    @SuppressWarnings("unchecked")
    private void consumePayload(final String stream, final MessageHandler.Context context, final Message message) {
        reactor.execute(() -> Promise.ofBlocking(executor, () ->
                        JsonUtils.bytes2Object(message.getBodyAsBinary(), Payload.class))
                .then(payload -> cryptoBybitAnalyst.analyze(payload))
                .whenComplete((_, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Failed to process stream message: {}", ex);
                    } else {
                        streamOffsetsRepository.upsertOffset(stream, context.offset());
                    }
                })
        );
    }

    public Promise<?> publish(final Payload<Map<String, Object>> payload) {
        final var provider = payload.getProvider();
        final var source = payload.getSource();
        if (!Provider.BYBIT_TA.equals(provider)) {
            LOGGER.debug("Skipping publish: no stream route for provider={} source={}", provider, source);
            return Promise.of(null);
        }

        final var settablePromise = new SettablePromise<Void>();
        try {
            final var message = producer.messageBuilder()
                    .addData(JsonUtils.object2Bytes(payload)) // todo: may be implement it as a blocking call
                    .build();
            producer.send(message, confirmationStatus ->
                    reactor.execute(() -> {
                        if (confirmationStatus.isConfirmed()) {
                            settablePromise.set(null);
                        } else {
                            settablePromise.setException(new RuntimeException("Stream publish not confirmed: " +
                                    confirmationStatus));
                        }
                    })
            );
        } catch (final Exception ex) {
            LOGGER.error("Failed to publish payload to stream: {}", ex.getMessage(), ex);
            settablePromise.setException(ex);
        }

        return settablePromise;
    }

    private void closeConsumer(final Consumer consumer) {
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing stream consumer", ex);
        }
    }

    private void closeProducer(final Producer producer) {
        try {
            if (producer != null) {
                producer.close();
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing stream producer", ex);
        }
    }

    private void closeEnvironment() {
        try {
            if (environment != null) {
                environment.close();
                environment = null;
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing stream environment", ex);
        }
    }
}
