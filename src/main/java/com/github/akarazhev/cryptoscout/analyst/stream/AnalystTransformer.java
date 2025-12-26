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

import com.github.akarazhev.cryptoscout.analyst.DataService;
import com.github.akarazhev.jcryptolib.stream.Payload;
import com.github.akarazhev.jcryptolib.stream.Provider;
import io.activej.datastream.processor.transformer.AbstractStreamTransformer;
import io.activej.datastream.supplier.StreamDataAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class AnalystTransformer extends AbstractStreamTransformer<StreamPayload, StreamPayload> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalystTransformer.class);
    private final DataService dataService;
    private final Function<Payload<Map<String, Object>>, Payload<Map<String, Object>>> preprocessor;

    public static AnalystTransformer createForBybit(final DataService dataService) {
        return new AnalystTransformer(dataService, AnalystTransformer::bybitPreprocessor);
    }

    public static AnalystTransformer createForCryptoScout(final DataService dataService) {
        return new AnalystTransformer(dataService, AnalystTransformer::cryptoScoutPreprocessor);
    }

    private AnalystTransformer(final DataService dataService,
                               final Function<Payload<Map<String, Object>>, Payload<Map<String, Object>>> preprocessor) {
        super();
        this.dataService = dataService;
        this.preprocessor = preprocessor;
    }

    @Override
    protected StreamDataAcceptor<StreamPayload> onResumed(final StreamDataAcceptor<StreamPayload> output) {
        return in -> {
            try {
                final var payload = in.payload();
                if (payload == null) {
                    output.accept(new StreamPayload(in.stream(), in.offset(), null));
                    return;
                }

                final var preprocessed = preprocessor.apply(payload);
                if (preprocessed == null) {
                    output.accept(new StreamPayload(in.stream(), in.offset(), null));
                    return;
                }

                final BiConsumer<Payload<Map<String, Object>>, Exception> callback = (result, error) -> {
                    if (reactor.inReactorThread()) {
                        handleCallback(output, in, result, error);
                    } else {
                        reactor.execute(() -> handleCallback(output, in, result, error));
                    }
                };

                dataService.processAsync(preprocessed, callback);
            } catch (final Exception ex) {
                LOGGER.error("AnalystTransformer failed at offset {} for stream {}: {}",
                        in.offset(), in.stream(), ex.getMessage(), ex);
                output.accept(new StreamPayload(in.stream(), in.offset(), null));
            }
        };
    }

    private void handleCallback(final StreamDataAcceptor<StreamPayload> output, final StreamPayload in,
                                final Payload<Map<String, Object>> result, final Exception error) {
        if (error != null) {
            LOGGER.error("DataService processing error at offset {} for stream {}: {}",
                    in.offset(), in.stream(), error.getMessage(), error);
            output.accept(new StreamPayload(in.stream(), in.offset(), null));
        } else {
            output.accept(new StreamPayload(in.stream(), in.offset(), result));
        }
    }

    private static Payload<Map<String, Object>> bybitPreprocessor(final Payload<Map<String, Object>> payload) {
        if (!Provider.BYBIT.equals(payload.getProvider())) {
            return null;
        }

        return Payload.of(Provider.BYBIT_TA, payload.getSource(), payload.getData());
    }

    private static Payload<Map<String, Object>> cryptoScoutPreprocessor(final Payload<Map<String, Object>> payload) {
        return payload;
    }
}
