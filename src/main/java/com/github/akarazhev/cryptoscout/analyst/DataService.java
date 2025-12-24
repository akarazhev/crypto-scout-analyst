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

import com.github.akarazhev.jcryptolib.stream.Message;
import com.github.akarazhev.jcryptolib.util.JsonUtils;
import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class DataService extends AbstractReactive {
    private final static Logger LOGGER = LoggerFactory.getLogger(DataService.class);
    private final BybitStreamService bybitStreamService;
    private final CryptoScoutService cryptoScoutService;
    private final AmqpPublisher chatbotPublisher;
    private final AmqpPublisher collectorPublisher;

    public static DataService create(final NioReactor reactor,
                                     final BybitStreamService bybitStreamService,
                                     final CryptoScoutService cryptoScoutService,
                                     final AmqpPublisher chatbotPublisher,
                                     final AmqpPublisher collectorPublisher) {
        return new DataService(reactor, bybitStreamService, cryptoScoutService, chatbotPublisher, collectorPublisher);
    }

    private DataService(final NioReactor reactor,
                        final BybitStreamService bybitStreamService,
                        final CryptoScoutService cryptoScoutService,
                        final AmqpPublisher chatbotPublisher,
                        final AmqpPublisher collectorPublisher) {
        super(reactor);
        this.bybitStreamService = bybitStreamService;
        this.cryptoScoutService = cryptoScoutService;
        this.chatbotPublisher = chatbotPublisher;
        this.collectorPublisher = collectorPublisher;
    }

    @SuppressWarnings("unchecked")
    public void consume(final byte[] body) {
        try {
            consume((Message<List<Object>>) JsonUtils.bytes2Object(body, Message.class));
        } catch (final Exception e) {
            LOGGER.error("Failed to process message", e);
        }
    }

    private void consume(final Message<List<Object>> message) {
        final var command = message.command();
        switch (command.type()) {
            case Message.Type.RESPONSE -> {
                switch (command.method()) {
                    // CryptoScoutCollector methods
                    case Constants.Method.CRYPTO_SCOUT_GET_KLINE_1D -> {
                    }

                    case Constants.Method.CRYPTO_SCOUT_GET_KLINE_1W -> {
                    }

                    case Constants.Method.CRYPTO_SCOUT_GET_FGI -> {
                    }

                    // BybitCryptoCollector methods
                    case Constants.Method.BYBIT_GET_KLINE_1M -> {
                    }

                    case Constants.Method.BYBIT_GET_KLINE_5M -> {
                    }

                    case Constants.Method.BYBIT_GET_KLINE_15M -> {
                    }

                    case Constants.Method.BYBIT_GET_KLINE_60M -> {
                    }

                    case Constants.Method.BYBIT_GET_KLINE_240M -> {
                    }

                    case Constants.Method.BYBIT_GET_KLINE_1D -> {
                    }

                    case Constants.Method.BYBIT_GET_TICKER -> {
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_1 -> {
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_50 -> {
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_200 -> {
                    }

                    case Constants.Method.BYBIT_GET_ORDER_BOOK_1000 -> {
                    }

                    case Constants.Method.BYBIT_GET_PUBLIC_TRADE -> {
                    }

                    case Constants.Method.BYBIT_GET_ALL_LIQUIDATION -> {
                    }

                    default -> LOGGER.debug("Unhandled response method: {}", command.method());
                }
            }

            default -> LOGGER.debug("Unhandled message type: {}", command.type());
        }
    }
}
