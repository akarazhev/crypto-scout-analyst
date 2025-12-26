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

package com.github.akarazhev.cryptoscout.module;

import com.github.akarazhev.cryptoscout.analyst.AmqpConsumer;
import com.github.akarazhev.cryptoscout.analyst.AmqpPublisher;
import com.github.akarazhev.cryptoscout.analyst.BybitStreamService;
import com.github.akarazhev.cryptoscout.analyst.CryptoScoutService;
import com.github.akarazhev.cryptoscout.analyst.DataService;
import com.github.akarazhev.cryptoscout.analyst.StreamService;
import com.github.akarazhev.cryptoscout.analyst.db.AnalystDataSource;
import com.github.akarazhev.cryptoscout.analyst.db.StreamOffsetsRepository;
import com.github.akarazhev.cryptoscout.config.AmqpConfig;
import io.activej.inject.annotation.Eager;
import io.activej.inject.annotation.Named;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.reactor.nio.NioReactor;

import java.util.concurrent.Executor;

import static com.github.akarazhev.cryptoscout.module.Constants.Config.ANALYST_CONSUMER;
import static com.github.akarazhev.cryptoscout.module.Constants.Config.ANALYST_CONSUMER_CLIENT_NAME;
import static com.github.akarazhev.cryptoscout.module.Constants.Config.CHATBOT_PUBLISHER;
import static com.github.akarazhev.cryptoscout.module.Constants.Config.CHATBOT_PUBLISHER_CLIENT_NAME;
import static com.github.akarazhev.cryptoscout.module.Constants.Config.COLLECTOR_PUBLISHER;
import static com.github.akarazhev.cryptoscout.module.Constants.Config.COLLECTOR_PUBLISHER_CLIENT_NAME;

public final class AnalystModule extends AbstractModule {

    private AnalystModule() {
    }

    public static AnalystModule create() {
        return new AnalystModule();
    }

    @Provides
    private AnalystDataSource analystDataSource(final NioReactor reactor, final Executor executor) {
        return AnalystDataSource.create(reactor, executor);
    }

    @Provides
    @Named(CHATBOT_PUBLISHER)
    @Eager
    private AmqpPublisher chatbotPublisher(final NioReactor reactor, final Executor executor) {
        return AmqpPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(), CHATBOT_PUBLISHER_CLIENT_NAME,
                AmqpConfig.getAmqpChatbotQueue());
    }

    @Provides
    @Named(COLLECTOR_PUBLISHER)
    @Eager
    private AmqpPublisher collectorPublisher(final NioReactor reactor, final Executor executor) {
        return AmqpPublisher.create(reactor, executor, AmqpConfig.getConnectionFactory(), COLLECTOR_PUBLISHER_CLIENT_NAME,
                AmqpConfig.getAmqpAnalystQueue());
    }

    @Provides
    @Named(ANALYST_CONSUMER)
    @Eager
    private AmqpConsumer analystConsumer(final NioReactor reactor, final Executor executor,
                                         final DataService dataService) {

        final var consumer = AmqpConsumer.create(reactor, executor, AmqpConfig.getConnectionFactory(),
                ANALYST_CONSUMER_CLIENT_NAME, AmqpConfig.getAmqpCollectorQueue());
        consumer.getStreamSupplier().streamTo(dataService.getStreamConsumer());
        return consumer;
    }

    @Provides
    private StreamOffsetsRepository streamOffsetsRepository(final NioReactor reactor,
                                                            final AnalystDataSource analystDataSource) {
        return StreamOffsetsRepository.create(reactor, analystDataSource);
    }

    @Provides
    private BybitStreamService bybitStreamService(final NioReactor reactor, final Executor executor,
                                                  final StreamOffsetsRepository streamOffsetsRepository,
                                                  final DataService dataService) {
        return BybitStreamService.create(reactor, executor, streamOffsetsRepository, dataService);
    }

    @Provides
    private CryptoScoutService cryptoScoutService(final NioReactor reactor, final Executor executor,
                                                  final StreamOffsetsRepository streamOffsetsRepository,
                                                  final DataService dataService) {
        return CryptoScoutService.create(reactor, executor, streamOffsetsRepository, dataService);
    }

    @Provides
    @Eager
    private StreamService streamService(final NioReactor reactor, final BybitStreamService bybitStreamService,
                                        final CryptoScoutService cryptoScoutService) {
        return StreamService.create(reactor, bybitStreamService, cryptoScoutService);
    }

    @Provides
    private DataService dataService(@Named(CHATBOT_PUBLISHER) final AmqpPublisher chatbotPublisher,
                                    @Named(COLLECTOR_PUBLISHER) final AmqpPublisher collectorPublisher,
                                    final Executor executor) {
        return DataService.create(chatbotPublisher, collectorPublisher, executor);
    }
}
