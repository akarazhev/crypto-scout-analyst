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

package com.github.akarazhev.cryptoscout.analyst;

import io.activej.async.service.ReactiveService;
import io.activej.promise.Promise;
import io.activej.reactor.AbstractReactive;
import io.activej.reactor.nio.NioReactor;

public final class StreamService extends AbstractReactive implements ReactiveService {
    private final BybitStreamService bybitStreamService;
    private final CryptoScoutService cryptoScoutService;

    public static StreamService create(final NioReactor reactor, final BybitStreamService bybitStreamService,
                                       final CryptoScoutService cryptoScoutService) {
        return new StreamService(reactor, bybitStreamService, cryptoScoutService);
    }

    private StreamService(final NioReactor reactor, final BybitStreamService bybitStreamService,
                          final CryptoScoutService cryptoScoutService) {
        super(reactor);
        this.bybitStreamService = bybitStreamService;
        this.cryptoScoutService = cryptoScoutService;
    }

    @Override
    public Promise<Void> start() {
        return bybitStreamService.start()
                .then(cryptoScoutService::start);
    }

    @Override
    public Promise<Void> stop() {
        return bybitStreamService.stop()
                .then(cryptoScoutService::stop);
    }
}
