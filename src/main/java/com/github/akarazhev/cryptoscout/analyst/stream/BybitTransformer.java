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

import com.github.akarazhev.jcryptolib.stream.Payload;
import com.github.akarazhev.jcryptolib.stream.Provider;
import io.activej.datastream.processor.transformer.AbstractStreamTransformer;
import io.activej.datastream.supplier.StreamDataAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class BybitTransformer extends AbstractStreamTransformer<StreamPayload, StreamPayload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BybitTransformer.class);

    public static BybitTransformer create(final List<Map<String, Object>> objects) {
        return new BybitTransformer(objects);
    }

    private BybitTransformer(final List<Map<String, Object>> objects) {
        super();
    }

    @Override
    protected StreamDataAcceptor<StreamPayload> onResumed(final StreamDataAcceptor<StreamPayload> output) {
        return in -> {
            try {
                final var payload = in.payload();
                if (payload == null || !Provider.BYBIT.equals(payload.getProvider())) {
                    // Not BYBIT: commit offset downstream without publishing
                    output.accept(new StreamPayload(in.stream(), in.offset(), null));
                    return;
                }

                // Perform analysis - blocking operations should be done in performAnalysis
                // which runs on virtual thread via executor in the caller (StreamPublisher handles backpressure)
                final var analyzedData = performAnalysis(payload.getData());
                final var analyzed = Payload.of(Provider.BYBIT_TA, payload.getSource(), analyzedData);
                output.accept(new StreamPayload(in.stream(), in.offset(), analyzed));
            } catch (final Exception ex) {
                LOGGER.error("Analysis failed at offset {} for stream {}: {}",
                        in.offset(), in.stream(), ex.getMessage(), ex);
                // Skip failed message but commit offset
                output.accept(new StreamPayload(in.stream(), in.offset(), null));
            }
        };
    }

    private Map<String, Object> performAnalysis(final Map<String, Object> data) {
        // TODO: Add your blocking analysis logic here
        // Examples:
        // - Call external API for technical indicators
        // - Perform heavy computation
        // - Query database for historical data
        return data;
    }
}
