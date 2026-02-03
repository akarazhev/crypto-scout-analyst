/*
 * MIT License
 *
 * Copyright (c) 2026 Andrey Karazhev
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

package com.github.akarazhev.cryptoscout.analyst.stream;

import com.github.akarazhev.cryptoscout.analyst.db.StreamOffsetsRepository;
import com.github.akarazhev.jcryptolib.util.JsonUtils;
import com.rabbitmq.stream.Producer;
import io.activej.datastream.consumer.AbstractStreamConsumer;
import io.activej.datastream.supplier.StreamDataAcceptor;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

public final class StreamPublisher extends AbstractStreamConsumer<StreamPayload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamPublisher.class);
    private final Producer producer;
    private final StreamOffsetsRepository offsetsRepository;
    private final Executor executor;
    private StreamDataAcceptor<StreamPayload> acceptor;

    public static StreamPublisher create(final Producer producer, final StreamOffsetsRepository offsetsRepository,
                                         final Executor executor) {
        return new StreamPublisher(producer, offsetsRepository, executor);
    }

    private StreamPublisher(final Producer producer, final StreamOffsetsRepository offsetsRepository,
                           final Executor executor) {
        this.producer = producer;
        this.offsetsRepository = offsetsRepository;
        this.executor = executor;
    }

    @Override
    protected void onStarted() {
        acceptor = this::handle;
        resume(acceptor);
    }

    private void handle(final StreamPayload in) {
        // Ensure one-at-a-time processing to respect backpressure and commit per message
        suspend();
        try {
            if (in.payload() == null) {
                // No publish, only commit offset
                Promise.ofBlocking(executor, () -> offsetsRepository.upsertOffset(in.stream(), in.offset()))
                        .whenException(ex -> LOGGER.warn("Failed to upsert offset for stream {} at {}: {}",
                                in.stream(), in.offset(), ex.getMessage(), ex))
                        .whenResult(() -> resume(acceptor));
                return;
            }

            final var message = producer.messageBuilder()
                    .addData(JsonUtils.object2Bytes(in.payload()))
                    .build();
            producer.send(message, status -> reactor.execute(() -> {
                if (!status.isConfirmed()) {
                    closeEx(new RuntimeException("Publish not confirmed: " + status));
                    return;
                }
                // Update offset for the SOURCE stream after successful publish
                Promise.ofBlocking(executor, () -> offsetsRepository.upsertOffset(in.stream(), in.offset()))
                        .whenException(ex -> LOGGER.warn("Failed to upsert offset for stream {} at {}: {}",
                                in.stream(), in.offset(), ex.getMessage(), ex))
                        .whenResult(() -> resume(acceptor));
            }));
        } catch (final Exception ex) {
            LOGGER.error("Failed to publish payload: {}", ex.getMessage(), ex);
            closeEx(ex);
        }
    }
}
