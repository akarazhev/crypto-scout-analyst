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
import io.activej.async.service.ReactiveService;
import io.activej.datastream.consumer.AbstractStreamConsumer;
import io.activej.datastream.consumer.StreamConsumer;
import io.activej.promise.Promise;
import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_FGI;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_KLINE_1D;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_KLINE_1W;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Source.ANALYST;
import static com.github.akarazhev.jcryptolib.bybit.Constants.Symbol.BTC_USDT;
import static com.github.akarazhev.jcryptolib.util.TimeUtils.tomorrowInUtc;

public final class DataService extends AbstractReactive implements ReactiveService {
    private final static Logger LOGGER = LoggerFactory.getLogger(DataService.class);
    private final Queue<Map<String, Object>> objects = new ArrayDeque<>();
    private final AmqpPublisher chatbotPublisher;
    private final AmqpPublisher collectorPublisher;
    private final Executor executor;

    public static DataService create(final NioReactor reactor, final Executor executor,
                                     final AmqpPublisher chatbotPublisher, final AmqpPublisher collectorPublisher) {
        return new DataService(reactor, executor, chatbotPublisher, collectorPublisher);
    }

    private DataService(final NioReactor reactor, final Executor executor,
                        final AmqpPublisher chatbotPublisher, final AmqpPublisher collectorPublisher) {
        super(reactor);
        this.executor = executor;
        this.chatbotPublisher = chatbotPublisher;
        this.collectorPublisher = collectorPublisher;
    }

    @Override
    public Promise<Void> start() {
        final var to = OffsetDateTime.ofInstant(Instant.ofEpochSecond(tomorrowInUtc()), ZoneId.of("UTC"));
        getCryptoScoutFgi(to);
        getCryptoScoutBtcUsdtKline1d(to);
        getCryptoScoutBtcUsdtKline1w(to);
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        objects.clear();
        return Promise.complete();
    }

    public StreamConsumer<byte[]> getStreamConsumer() {
        return new InternalStreamConsumer();
    }

    @SuppressWarnings("unchecked")
    private void consume(final byte[] body) {
        try {
            consume((Message<List<Map<String, Object>>>) JsonUtils.bytes2Object(body, Message.class));
        } catch (final Exception e) {
            LOGGER.error("Failed to process message", e);
        }
    }

    private void consume(final Message<List<Map<String, Object>>> message) {
        final var command = message.command();
        switch (command.type()) {
            case Message.Type.RESPONSE -> {
                switch (command.method()) {
                    // CryptoScoutCollector methods
                    case Constants.Method.CRYPTO_SCOUT_GET_KLINE_1D -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.CRYPTO_SCOUT_GET_KLINE_1W -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.CRYPTO_SCOUT_GET_FGI -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    // BybitCryptoCollector methods
                    case Constants.Method.BYBIT_GET_KLINE_1M -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_KLINE_5M -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_KLINE_15M -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_KLINE_60M -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_KLINE_240M -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_KLINE_1D -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_TICKER -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_1 -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_50 -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_200 -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_1000 -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_PUBLIC_TRADE -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    case Constants.Method.BYBIT_GET_ALL_LIQUIDATION -> {
                        final var value = message.value();
                        objects.addAll(value);
                    }

                    default -> LOGGER.debug("Unhandled response method: {}", command.method());
                }
            }

            default -> LOGGER.debug("Unhandled message type: {}", command.type());
        }
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

    private void getCryptoScoutFgi(final OffsetDateTime to) {
        // 2018-02-01 00:00:00+00
        final var from = OffsetDateTime.of(LocalDateTime.of(2018, 2, 1, 0, 0),
                ZoneOffset.UTC);
        collectorPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(), AmqpConfig.getAmqpCollectorRoutingKey(),
                Message.of(Message.Command.of(Message.Type.REQUEST, ANALYST, CRYPTO_SCOUT_GET_FGI),
                        new Object[]{from, to}));
    }

    private void getCryptoScoutBtcUsdtKline1d(final OffsetDateTime to) {
        // 2010-07-13 00:00:00+00
        final var from = OffsetDateTime.of(LocalDateTime.of(2010, 7, 13, 0, 0),
                ZoneOffset.UTC);
        collectorPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(), AmqpConfig.getAmqpCollectorRoutingKey(),
                Message.of(Message.Command.of(Message.Type.REQUEST, ANALYST, CRYPTO_SCOUT_GET_KLINE_1D),
                        new Object[]{BTC_USDT, from, to}));
    }

    private void getCryptoScoutBtcUsdtKline1w(final OffsetDateTime to) {
        // 2013-04-22 00:00:00+00
        final var from = OffsetDateTime.of(LocalDateTime.of(2013, 4, 22, 0, 0),
                ZoneOffset.UTC);
        collectorPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(), AmqpConfig.getAmqpCollectorRoutingKey(),
                Message.of(Message.Command.of(Message.Type.REQUEST, ANALYST, CRYPTO_SCOUT_GET_KLINE_1W),
                        new Object[]{BTC_USDT, from, to}));
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
