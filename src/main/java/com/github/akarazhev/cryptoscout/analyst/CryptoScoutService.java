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
import com.github.akarazhev.cryptoscout.analyst.stream.BytesToPayloadTransformer;
import com.github.akarazhev.cryptoscout.analyst.stream.CryptoScoutTransformer;
import com.github.akarazhev.cryptoscout.analyst.stream.MessageSupplier;
import com.github.akarazhev.cryptoscout.analyst.stream.StreamPublisher;
import com.github.akarazhev.cryptoscout.config.AmqpConfig;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.Producer;
import com.rabbitmq.stream.SubscriptionListener;
import io.activej.async.service.ReactiveService;
import io.activej.promise.Promise;
import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

public final class CryptoScoutService extends AbstractReactive implements ReactiveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoScoutService.class);
    private final Executor executor;
    private final StreamOffsetsRepository streamOffsetsRepository;
    private final String stream;
    private volatile Environment environment;
    private volatile Consumer consumer;
    private volatile Producer producer;
    private volatile MessageSupplier messageSupplier;

    public static CryptoScoutService create(final NioReactor reactor, final Executor executor,
                                            final StreamOffsetsRepository streamOffsetsRepository) {
        return new CryptoScoutService(reactor, executor, streamOffsetsRepository);
    }

    private CryptoScoutService(final NioReactor reactor, final Executor executor,
                               final StreamOffsetsRepository streamOffsetsRepository) {
        super(reactor);
        this.executor = executor;
        this.streamOffsetsRepository = streamOffsetsRepository;
        this.stream = AmqpConfig.getAmqpCryptoScoutStream();
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
                    environment = AmqpConfig.getEnvironment();
                    producer = environment.producerBuilder()
                            .name(stream)
                            .stream(stream)
                            .build();
                })
                .then(() -> {
                    messageSupplier = new MessageSupplier();
                    messageSupplier.transformWith(new BytesToPayloadTransformer())
                            .transformWith(new CryptoScoutTransformer())
                            .streamTo(new StreamPublisher(producer, streamOffsetsRepository, executor));
                    return Promise.ofBlocking(executor, () -> {
                        consumer = environment.consumerBuilder()
                                .stream(stream)
                                .noTrackingStrategy()
                                .subscriptionListener(this::updateOffset)
                                .messageHandler(this::onMessage)
                                .build();
                    });
                });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            closeConsumer();
            closeProducer();
            stopPipeline();
            closeEnvironment();
        });
    }

    private void updateOffset(final SubscriptionListener.SubscriptionContext context) {
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

    private void onMessage(final MessageHandler.Context context, final Message message) {
        try {
            final var body = message.getBodyAsBinary();
            messageSupplier.enqueue(stream, context.offset(), body);
        } catch (final Exception ex) {
            LOGGER.error("Failed to enqueue crypto scout stream message: {}", ex.getMessage(), ex);
        }
    }

    private void closeConsumer() {
        try {
            if (consumer != null) {
                consumer.close();
                consumer = null;
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing crypto scout stream consumer", ex);
        }
    }

    private void closeProducer() {
        try {
            if (producer != null) {
                producer.close();
                producer = null;
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing crypto scout stream producer", ex);
        }
    }

    private void stopPipeline() {
        final var supplier = messageSupplier;
        if (supplier != null) {
            reactor.execute(supplier::sendEndOfStream);
            messageSupplier = null;
        }
    }

    private void closeEnvironment() {
        try {
            if (environment != null) {
                environment.close();
                environment = null;
            }
        } catch (final Exception ex) {
            LOGGER.warn("Error closing crypto scout stream environment", ex);
        }
    }
}
