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

import com.github.akarazhev.cryptoscout.analyst.AmqpClient;
import com.github.akarazhev.cryptoscout.analyst.CryptoBybitAnalyst;
import com.github.akarazhev.cryptoscout.analyst.db.AnalystDataSource;
import com.github.akarazhev.cryptoscout.analyst.db.StreamOffsetsRepository;
import io.activej.inject.annotation.Eager;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.reactor.nio.NioReactor;

import java.util.concurrent.Executor;

public final class AnalystModule extends AbstractModule {

    private AnalystModule() {
    }

    public static AnalystModule create() {
        return new AnalystModule();
    }

    @Provides
    private AnalystDataSource jdbcDataSource(final NioReactor reactor, final Executor executor) {
        return AnalystDataSource.create(reactor, executor);
    }

    @Provides
    private StreamOffsetsRepository streamOffsetsRepository(final NioReactor reactor,
                                                            final AnalystDataSource analystDataSource) {
        return StreamOffsetsRepository.create(reactor, analystDataSource);
    }

    @Provides
    private CryptoBybitAnalyst cryptoBybitAnalyst(final NioReactor reactor, final Executor executor) {
        return CryptoBybitAnalyst.create(reactor, executor);
    }

    @Provides
    @Eager
    private AmqpClient AmqpClient(final NioReactor reactor, final Executor executor,
                                  final StreamOffsetsRepository streamOffsetsRepository,
                                  final CryptoBybitAnalyst cryptoBybitAnalyst) {
        return AmqpClient.create(reactor, executor, streamOffsetsRepository, cryptoBybitAnalyst);
    }
}
