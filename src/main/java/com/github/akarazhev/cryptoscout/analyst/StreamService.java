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
import com.github.akarazhev.cryptoscout.analyst.stream.BybitTransformer;
import com.github.akarazhev.cryptoscout.analyst.stream.BytesToPayloadTransformer;
import com.github.akarazhev.cryptoscout.analyst.stream.CryptoScoutTransformer;
import com.github.akarazhev.cryptoscout.analyst.stream.MessageSupplier;
import com.github.akarazhev.cryptoscout.analyst.stream.StreamPublisher;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler;
import com.rabbitmq.stream.Producer;
import com.rabbitmq.stream.SubscriptionListener;
import io.activej.async.service.ReactiveService;
import io.activej.promise.Promise;

import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

import com.github.akarazhev.cryptoscout.config.AmqpConfig;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.OffsetSpecification;

public final class StreamService extends AbstractReactive implements ReactiveService {
    private final static Logger LOGGER = LoggerFactory.getLogger(StreamService.class);
    private final Executor executor;
    private final StreamOffsetsRepository streamOffsetsRepository;
    private volatile Environment environment;
    private volatile Consumer bybitStreamConsumer;
    private volatile Producer bybitStreamTaProducer;
    private volatile MessageSupplier bybitMessageSupplier;
    private volatile Consumer cryptoScoutStreamConsumer;
    private volatile Producer cryptoScoutStreamProducer;
    private volatile MessageSupplier cryptoScoutMessageSupplier;

    public static StreamService create(final NioReactor reactor, final Executor executor,
                                       final StreamOffsetsRepository streamOffsetsRepository) {
        return new StreamService(reactor, executor, streamOffsetsRepository);
    }

    private StreamService(final NioReactor reactor, final Executor executor,
                          final StreamOffsetsRepository streamOffsetsRepository) {
        super(reactor);
        this.executor = executor;
        this.streamOffsetsRepository = streamOffsetsRepository;
    }

    @Override
    public Promise<?> start() {
        final var bybitStream = AmqpConfig.getAmqpBybitStream();
        final var bybitTaStream = AmqpConfig.getAmqpBybitTaStream();
        final var cryptoScoutStream = AmqpConfig.getAmqpCryptoScoutStream();
        // 1) Create RMQ environment + producers in blocking executor
        return Promise.ofBlocking(executor, () -> {
                    environment = AmqpConfig.getEnvironment();
                    bybitStreamTaProducer = environment.producerBuilder()
                            .name(bybitTaStream)
                            .stream(bybitTaStream)
                            .build();
                    cryptoScoutStreamProducer = environment.producerBuilder()
                            .name(cryptoScoutStream)
                            .stream(cryptoScoutStream)
                            .build();
                })
                // 2) Build ActiveJ datastream pipelines on reactor thread
                .then(() -> {
                    // Bybit stream pipeline
                    bybitMessageSupplier = new MessageSupplier();
                    bybitMessageSupplier.transformWith(new BytesToPayloadTransformer())
                            .transformWith(new BybitTransformer())
                            .streamTo(new StreamPublisher(bybitStreamTaProducer, streamOffsetsRepository, executor));
                    // Crypto Scout stream pipeline
                    cryptoScoutMessageSupplier = new MessageSupplier();
                    cryptoScoutMessageSupplier.transformWith(new BytesToPayloadTransformer())
                            .transformWith(new CryptoScoutTransformer())
                            .streamTo(new StreamPublisher(cryptoScoutStreamProducer, streamOffsetsRepository, executor));
                    // 3) Create RMQ consumers in blocking executor
                    return Promise.ofBlocking(executor, () -> {
                        bybitStreamConsumer = environment.consumerBuilder()
                                .stream(bybitStream)
                                .noTrackingStrategy()
                                .subscriptionListener(c -> updateOffset(bybitStream, c))
                                .messageHandler((c, m) -> onBybitMessage(bybitStream, c, m))
                                .build();
                        cryptoScoutStreamConsumer = environment.consumerBuilder()
                                .stream(cryptoScoutStream)
                                .noTrackingStrategy()
                                .subscriptionListener(c -> updateOffset(cryptoScoutStream, c))
                                .messageHandler((c, m) -> onCryptoScoutMessage(cryptoScoutStream, c, m))
                                .build();
                    });
                });
    }

    @Override
    public Promise<?> stop() {
        return Promise.ofBlocking(executor, () -> {
            closeConsumer(bybitStreamConsumer);
            bybitStreamConsumer = null;
            closeConsumer(cryptoScoutStreamConsumer);
            cryptoScoutStreamConsumer = null;
            closeProducer(bybitStreamTaProducer);
            bybitStreamTaProducer = null;
            closeProducer(cryptoScoutStreamProducer);
            cryptoScoutStreamProducer = null;
            // Gracefully stop datastream pipelines
            if (bybitMessageSupplier != null) {
                reactor.execute(() -> bybitMessageSupplier.sendEndOfStream());
                bybitMessageSupplier = null;
            }
            if (cryptoScoutMessageSupplier != null) {
                reactor.execute(() -> cryptoScoutMessageSupplier.sendEndOfStream());
                cryptoScoutMessageSupplier = null;
            }

            closeEnvironment();
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

    private void onBybitMessage(final String sourceStream, final MessageHandler.Context context, final Message message) {
        // Push to bybit datastream supplier; offset will be stored after publish in the pipeline
        try {
            final var body = message.getBodyAsBinary();
            bybitMessageSupplier.enqueue(sourceStream, context.offset(), body);
        } catch (final Exception ex) {
            LOGGER.error("Failed to enqueue bybit stream message: {}", ex.getMessage(), ex);
        }
    }

    private void onCryptoScoutMessage(final String sourceStream, final MessageHandler.Context context, final Message message) {
        // Push to crypto scout datastream supplier; offset will be stored after publish in the pipeline
        try {
            final var body = message.getBodyAsBinary();
            cryptoScoutMessageSupplier.enqueue(sourceStream, context.offset(), body);
        } catch (final Exception ex) {
            LOGGER.error("Failed to enqueue crypto scout stream message: {}", ex.getMessage(), ex);
        }
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
