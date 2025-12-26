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

import com.github.akarazhev.cryptoscout.config.AmqpConfig;
import com.github.akarazhev.jcryptolib.stream.Message;
import com.github.akarazhev.jcryptolib.stream.Payload;
import com.github.akarazhev.jcryptolib.util.JsonUtils;
import io.activej.datastream.consumer.AbstractStreamConsumer;
import io.activej.datastream.consumer.StreamConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public final class DataService {
    private final static Logger LOGGER = LoggerFactory.getLogger(DataService.class);
    private final AmqpPublisher chatbotPublisher;
    private final AmqpPublisher collectorPublisher;
    private final Executor executor;

    public static DataService create(final AmqpPublisher chatbotPublisher, final AmqpPublisher collectorPublisher,
                                     final Executor executor) {
        return new DataService(chatbotPublisher, collectorPublisher, executor);
    }

    private DataService(final AmqpPublisher chatbotPublisher, final AmqpPublisher collectorPublisher,
                        final Executor executor) {
        this.chatbotPublisher = chatbotPublisher;
        this.collectorPublisher = collectorPublisher;
        this.executor = executor;
    }

    public StreamConsumer<byte[]> getStreamConsumer() {
        return new InternalStreamConsumer();
    }

    @SuppressWarnings("unchecked")
    private void consume(final byte[] body) {
        try {
            consume((Message<List<Object>>) JsonUtils.bytes2Object(body, Message.class));
        } catch (final Exception e) {
            LOGGER.error("Failed to process message", e);
        }
    }

    private void consume(final Message<List<Object>> message) {
        final var command = message.command();
        if (command.type() != Message.Type.REQUEST) {
            LOGGER.debug("Unhandled message type: {}", command.type());
            return;
        }

        final var method = command.method();
        LOGGER.debug("Processing request method: {}", method);
    }

    public void processAsync(final Payload<Map<String, Object>> payload,
                              final BiConsumer<Payload<Map<String, Object>>, Exception> callback) {
        CompletableFuture.supplyAsync(() -> enrichPayload(payload), executor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOGGER.error("Failed to process payload: {}", error.getMessage(), error);
                        callback.accept(null, (Exception) error);
                    } else {
                        callback.accept(result, null);
                    }
                });
    }

    private Payload<Map<String, Object>> enrichPayload(final Payload<Map<String, Object>> payload) {
        return payload;
    }

    private <T> void publish(final String source, final String method, final T data) {
        final var command = Message.Command.of(Message.Type.RESPONSE, Constants.Source.COLLECTOR, method);
        final var message = Message.of(command, data);
        
        switch (source) {
            case Constants.Source.CHATBOT -> {
                chatbotPublisher.publish(
                        AmqpConfig.getAmqpCryptoScoutExchange(),
                        AmqpConfig.getAmqpChatbotRoutingKey(),
                        message
                );
            }
            case Constants.Source.COLLECTOR -> {
                collectorPublisher.publish(
                        AmqpConfig.getAmqpCryptoScoutExchange(),
                        AmqpConfig.getAmqpCollectorRoutingKey(),
                        message
                );
            }
            default -> LOGGER.warn("Unknown source for response: {}", source);
        }
    }

    private final class InternalStreamConsumer extends AbstractStreamConsumer<byte[]> {

        @Override
        protected void onStarted() {
            resume(DataService.this::consume);
        }

        @Override
        protected void onEndOfStream() {
            acknowledge();
        }

        @Override
        protected void onError(final Exception e) {
            LOGGER.error("Stream error in DataService consumer", e);
        }
    }
}
