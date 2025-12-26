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
import static com.github.akarazhev.cryptoscout.analyst.Constants.Source.ANALYST;
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

    private static AmqpTestPublisher analystQueuePublisher;
    private static AmqpTestConsumer collectorQueueConsumer;

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

        analystQueuePublisher = AmqpTestPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                AmqpConfig.getAmqpAnalystQueue());
        collectorQueueConsumer = AmqpTestConsumer.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                AmqpConfig.getAmqpCollectorQueue());

        TestUtils.await(analystQueuePublisher.start(), chatbotPublisher.start(), collectorPublisher.start());
    }

    @BeforeEach
    void resetState() {
        collectorQueueConsumer.stop();
    }

    @Test
    void serviceStartsSendsInitialRequests() {
        TestUtils.await(dataService.start().whenComplete(collectorQueueConsumer::start));
        final var message = TestUtils.await(collectorQueueConsumer.getMessage());

        assertNotNull(message);
        assertEquals(Message.Type.REQUEST, message.command().type());
        assertEquals(ANALYST, message.command().source());
        assertTrue(List.of(CRYPTO_SCOUT_GET_FGI, CRYPTO_SCOUT_GET_KLINE_1D, CRYPTO_SCOUT_GET_KLINE_1W)
                .contains(message.command().method()));
    }

    @Test
    void serviceStopsSuccessfully() {
        TestUtils.await(dataService.start());
        TestUtils.await(dataService.stop());
    }

    @Test
    void streamConsumerProcessesCryptoScoutFgiResponse() throws Exception {
        final var fgi = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.FGI);
        final var message = Message.of(Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_FGI),
                List.of(fgi));

        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
                AmqpConfig.getAmqpAnalystRoutingKey(), message));
        Thread.sleep(500);
    }

//    @Test
//    void streamConsumerProcessesCryptoScoutKline1dResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.KLINE_D);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_KLINE_1D),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesCryptoScoutKline1wResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.CRYPTO_SCOUT, MockData.Type.KLINE_W);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, CRYPTO_SCOUT_GET_KLINE_1W),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline1mResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_1);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_1M),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline5mResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_5);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_5M),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline15mResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_15);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_15M),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline60mResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_60);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_60M),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline240mResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_240);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_240M),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitKline1dResponse() throws Exception {
//        final var kline = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.KLINE_D);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_KLINE_1D),
//                List.of(kline)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitTickerResponse() throws Exception {
//        final var ticker = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.TICKERS);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_TICKER),
//                List.of(ticker)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitOrderBook1Response() throws Exception {
//        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_1);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_1),
//                List.of(ob)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitOrderBook50Response() throws Exception {
//        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_50);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_50),
//                List.of(ob)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitOrderBook200Response() throws Exception {
//        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_200);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_200),
//                List.of(ob)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitOrderBook1000Response() throws Exception {
//        final var ob = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.ORDER_BOOK_1000);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ORDER_BOOK_1000),
//                List.of(ob)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitPublicTradeResponse() throws Exception {
//        final var pt = MockData.get(MockData.Source.BYBIT_SPOT, MockData.Type.PUBLIC_TRADE);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_PUBLIC_TRADE),
//                List.of(pt)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerProcessesBybitAllLiquidationResponse() throws Exception {
//        final var al = MockData.get(MockData.Source.BYBIT_LINEAR, MockData.Type.ALL_LIQUIDATION);
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, BYBIT_GET_ALL_LIQUIDATION),
//                List.of(al)
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerIgnoresUnhandledMethod() throws Exception {
//        final var responseMessage = Message.of(
//                Message.Command.of(Message.Type.RESPONSE, COLLECTOR, "unknown.method"),
//                List.of(Map.of("test", "data"))
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), responseMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void streamConsumerIgnoresNonResponseMessages() throws Exception {
//        final var requestMessage = Message.of(
//                Message.Command.of(Message.Type.REQUEST, COLLECTOR, CRYPTO_SCOUT_GET_FGI),
//                List.of(Map.of("test", "data"))
//        );
//
//        TestUtils.await(analystQueuePublisher.publish(AmqpConfig.getAmqpCryptoScoutExchange(),
//                AmqpConfig.getAmqpAnalystRoutingKey(), requestMessage));
//        Thread.sleep(500);
//    }
//
//    @Test
//    void processAsyncEnrichesPayloadSuccessfully() throws Exception {
//        final Map<String, Object> data = Map.of("key", "value");
//        final var payload = Payload.of(Provider.BYBIT, Source.RAPI, data);
//        final var latch = new CountDownLatch(1);
//        final var resultRef = new AtomicReference<Payload<Map<String, Object>>>();
//        final var errorRef = new AtomicReference<Exception>();
//
//        dataService.processAsync(payload, (result, error) -> {
//            resultRef.set(result);
//            errorRef.set(error);
//            latch.countDown();
//        });
//
//        assertTrue(latch.await(5, TimeUnit.SECONDS));
//        assertNotNull(resultRef.get());
//        assertNull(errorRef.get());
//        assertEquals(Provider.BYBIT, resultRef.get().getProvider());
//        assertEquals(Source.RAPI, resultRef.get().getSource());
//    }
//
//    @AfterAll
//    static void cleanup() {
//        reactor.post(() -> collectorQueueConsumer.stop()
//                .whenComplete(() -> analystQueuePublisher.stop()
//                        .whenComplete(() -> chatbotPublisher.stop()
//                                .whenComplete(() -> collectorPublisher.stop()
//                                        .whenComplete(() -> dataService.stop()
//                                                .whenComplete(() -> reactor.breakEventloop()
//                                                ))))));
//        reactor.run();
//        executor.shutdown();
//        PodmanCompose.down();
//    }

    final static class Config {
        private Config() {
            throw new UnsupportedOperationException();
        }

        static final String ANALYST_CONSUMER_CLIENT_NAME = "analyst-consumer";
        static final String COLLECTOR_PUBLISHER_CLIENT_NAME = "collector-publisher";
        static final String CHATBOT_PUBLISHER_CLIENT_NAME = "chatbot-publisher";
    }
}
