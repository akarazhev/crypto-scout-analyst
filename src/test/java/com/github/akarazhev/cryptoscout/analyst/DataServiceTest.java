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
import com.github.akarazhev.cryptoscout.test.AmqpTestConsumer;
import com.github.akarazhev.cryptoscout.test.AmqpTestPublisher;
import com.github.akarazhev.cryptoscout.test.MockData;
import com.github.akarazhev.cryptoscout.test.PodmanCompose;
import com.github.akarazhev.jcryptolib.stream.Message;
import com.github.akarazhev.jcryptolib.stream.Payload;
import com.github.akarazhev.jcryptolib.stream.Provider;
import com.github.akarazhev.jcryptolib.stream.Source;
import io.activej.eventloop.Eventloop;
import io.activej.promise.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_ALL_LIQUIDATION;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_15M;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_1D;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_1M;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_240M;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_5M;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_KLINE_60M;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_ORDER_BOOK_1;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_ORDER_BOOK_1000;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_ORDER_BOOK_200;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_ORDER_BOOK_50;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_PUBLIC_TRADE;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.BYBIT_GET_TICKER;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_FGI;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_KLINE_1D;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Method.CRYPTO_SCOUT_GET_KLINE_1W;
import static com.github.akarazhev.cryptoscout.analyst.Constants.Source.COLLECTOR;
import static com.github.akarazhev.cryptoscout.analyst.DataServiceTest.Config.ANALYST_CONSUMER_CLIENT_NAME;
import static com.github.akarazhev.cryptoscout.analyst.DataServiceTest.Config.CHATBOT_PUBLISHER_CLIENT_NAME;
import static com.github.akarazhev.cryptoscout.analyst.DataServiceTest.Config.COLLECTOR_PUBLISHER_CLIENT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DataServiceTest {
    private static ExecutorService executor;
    private static Eventloop reactor;

    private static DataService dataService;
    private static AmqpPublisher chatbotPublisher;
    private static AmqpPublisher collectorPublisher;
    private static AmqpConsumer analystConsumer;

    private static AmqpTestPublisher analystTestPublisher;
    private static AmqpTestConsumer collectorTestConsumer;
    private static AmqpTestConsumer chatbotTestConsumer;

    @BeforeAll
    static void setup() {
        PodmanCompose.up();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        reactor = Eventloop.builder().withCurrentThread().build();

        chatbotPublisher = AmqpPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                CHATBOT_PUBLISHER_CLIENT_NAME, AmqpConfig.getAmqpChatbotQueue());
        collectorPublisher = AmqpPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                COLLECTOR_PUBLISHER_CLIENT_NAME, AmqpConfig.getAmqpCollectorQueue());
        dataService = DataService.create(reactor, executor, chatbotPublisher, collectorPublisher);
        analystConsumer = AmqpConsumer.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                ANALYST_CONSUMER_CLIENT_NAME, AmqpConfig.getAmqpAnalystQueue());
        analystConsumer.getStreamSupplier().streamTo(dataService.getStreamConsumer());

        analystTestPublisher = AmqpTestPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                AmqpConfig.getAmqpAnalystQueue());
        collectorTestConsumer = AmqpTestConsumer.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                AmqpConfig.getAmqpCollectorQueue());
        chatbotTestConsumer = AmqpTestConsumer.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                AmqpConfig.getAmqpChatbotQueue());

        TestUtils.await(analystTestPublisher.start(), chatbotPublisher.start(), collectorPublisher.start(),
                analystConsumer.start());
    }

    @BeforeEach
    void resetState() {
        TestUtils.await(collectorTestConsumer.stop(), chatbotTestConsumer.stop());
    }

    @Test
    void serviceStartPublishesInitialRequests() {
        TestUtils.await(dataService.start().whenComplete(collectorTestConsumer::start));
        final var message = TestUtils.await(collectorTestConsumer.getMessage());

        assertNotNull(message);
        assertEquals(Message.Type.REQUEST, message.command().type());
        assertEquals(Constants.Source.ANALYST, message.command().source());
        assertTrue(message.command().method().equals(CRYPTO_SCOUT_GET_FGI) ||
                message.command().method().equals(CRYPTO_SCOUT_GET_KLINE_1D) ||
                message.command().method().equals(CRYPTO_SCOUT_GET_KLINE_1W));
        assertNotNull(message.value());
    }

    @Test
    void serviceStopClearsState() {
        TestUtils.await(dataService.start());
        TestUtils.await(dataService.stop());
    }

    @Test
    void consumesCryptoScoutFgiResponse() throws Exception {
        final var fgi = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.FGI);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_FGI),
                List.of(fgi));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var fgis = dataService.getCryptoScoutFgis();
        assertNotNull(fgis);
        assertEquals(1, fgis.size());
        assertEquals(fgi, fgis.peek());
    }

    @Test
    void consumesCryptoScoutKline1dResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.KLINE_D);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_KLINE_1D),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getCryptoScoutKlines1d();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesCryptoScoutKline1wResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.KLINE_W);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_KLINE_1W),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getCryptoScoutKlines1w();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline1mResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_1);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_1M),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines1m();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline5mResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_5);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_5M),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines5m();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline15mResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_15);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_15M),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines15m();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline60mResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_60);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_60M),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines60m();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline240mResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_240);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_240M),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines240m();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitKline1dResponse() throws Exception {
        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_D);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_1D),
                List.of(kline));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var klines = dataService.getBybitKlines1d();
        assertNotNull(klines);
        assertEquals(1, klines.size());
        assertEquals(kline, klines.peek());
    }

    @Test
    void consumesBybitTickerResponse() throws Exception {
        final var ticker = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.TICKERS);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_TICKER),
                List.of(ticker));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var tickers = dataService.getBybitTickers();
        assertNotNull(tickers);
        assertEquals(1, tickers.size());
        assertEquals(ticker, tickers.peek());
    }

    @Test
    void consumesBybitOrderBook1Response() throws Exception {
        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_1);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_1),
                List.of(ob));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var orderBooks = dataService.getBybitOrderBooks1();
        assertNotNull(orderBooks);
        assertEquals(1, orderBooks.size());
        assertEquals(ob, orderBooks.peek());
    }

    @Test
    void consumesBybitOrderBook50Response() throws Exception {
        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_50);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_50),
                List.of(ob));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var orderBooks = dataService.getBybitOrderBooks50();
        assertNotNull(orderBooks);
        assertEquals(1, orderBooks.size());
        assertEquals(ob, orderBooks.peek());
    }

    @Test
    void consumesBybitOrderBook200Response() throws Exception {
        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_200);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_200),
                List.of(ob));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var orderBooks = dataService.getBybitOrderBooks200();
        assertNotNull(orderBooks);
        assertEquals(1, orderBooks.size());
        assertEquals(ob, orderBooks.peek());
    }

    @Test
    void consumesBybitOrderBook1000Response() throws Exception {
        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_1000);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_1000),
                List.of(ob));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var orderBooks = dataService.getBybitOrderBooks1000();
        assertNotNull(orderBooks);
        assertEquals(1, orderBooks.size());
        assertEquals(ob, orderBooks.peek());
    }

    @Test
    void consumesBybitPublicTradeResponse() throws Exception {
        final var pt = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.PUBLIC_TRADE);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_PUBLIC_TRADE),
                List.of(pt));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var publicTrades = dataService.getBybitPublicTrades();
        assertNotNull(publicTrades);
        assertEquals(1, publicTrades.size());
        assertEquals(pt, publicTrades.peek());
    }

    @Test
    void consumesBybitAllLiquidationResponse() throws Exception {
        final var al = MockData.get(MockData.Source.BYBIT_LINEAR, MockData.Type.ALL_LIQUIDATION);
        final var responseMessage = Message.of(
                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ALL_LIQUIDATION),
                List.of(al));

        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
        final var liquidations = dataService.getBybitAllLiquidations();
        assertNotNull(liquidations);
        assertEquals(1, liquidations.size());
        assertEquals(al, liquidations.peek());
    }

    @Test
    void processAsyncReturnsEnrichedPayload() throws Exception {
        final var latch = new CountDownLatch(1);
        final var resultRef = new AtomicReference<Payload<Map<String, Object>>>();
        final var errorRef = new AtomicReference<Exception>();

        final var data = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.FGI);
        final var payload = Payload.of(Provider.CMC, Source.FGI, data);

        dataService.processAsync(payload, (result, error) -> {
            resultRef.set(result);
            errorRef.set(error);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(resultRef.get());
        assertEquals(payload.getProvider(), resultRef.get().getProvider());
        assertEquals(payload.getSource(), resultRef.get().getSource());
        assertEquals(payload.getData(), resultRef.get().getData());
    }

    @Test
    void handlesInvalidMessageGracefully() throws Exception {
        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(),
                Message.of(Message.Command.of(Message.Type.RESPONSE, COLLECTOR, "unknown.method"),
                        List.of(Map.of("test", "data")))));
    }

    @Test
    void handlesUnknownMessageTypeGracefully() throws Exception {
        TestUtils.await(analystTestPublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(),
                Message.of(Message.Command.of(Message.Type.REQUEST, COLLECTOR, CRYPTO_SCOUT_GET_FGI),
                        List.of(Map.of("test", "data")))));
    }

    @AfterAll
    static void cleanup() {
        reactor.post(() -> collectorTestConsumer.stop()
                .whenComplete(() -> chatbotTestConsumer.stop()
                        .whenComplete(() -> analystTestPublisher.stop()
                                .whenComplete(() -> dataService.stop()
                                        .whenComplete(() -> chatbotPublisher.stop()
                                                .whenComplete(() -> collectorPublisher.stop()
                                                        .whenComplete(() -> analystConsumer.stop()
                                                                .whenComplete(() -> reactor.breakEventloop()
                                                                ))))))));
        reactor.run();
        executor.shutdown();
        PodmanCompose.down();
    }

    final static class Config {
        private Config() {
            throw new UnsupportedOperationException();
        }

        static final String ANALYST_CONSUMER_CLIENT_NAME = "analyst-consumer";
        static final String CHATBOT_PUBLISHER_CLIENT_NAME = "chatbot-publisher";
        static final String COLLECTOR_PUBLISHER_CLIENT_NAME = "collector-publisher";
    }
}
