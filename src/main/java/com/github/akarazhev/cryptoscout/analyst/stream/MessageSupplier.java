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

package com.github.akarazhev.cryptoscout.analyst.stream;

import io.activej.datastream.supplier.AbstractStreamSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageSupplier extends AbstractStreamSupplier<StreamIn> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSupplier.class);

    public void enqueue(final String stream, final long offset, final byte[] body) {
        // Ensure we schedule send on reactor thread
        if (reactor.inReactorThread()) {
            send(new StreamIn(stream, offset, body));
        } else {
            reactor.execute(() -> send(new StreamIn(stream, offset, body)));
        }
    }

    @Override
    protected void onError(final Exception e) {
        LOGGER.error("RabbitMessageSupplier error: {}", e.getMessage(), e);
    }
}
