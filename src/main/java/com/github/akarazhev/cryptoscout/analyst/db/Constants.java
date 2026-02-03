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

package com.github.akarazhev.cryptoscout.analyst.db;

public final class Constants {
    private Constants() {
        throw new UnsupportedOperationException();
    }

    public final static class Offsets {
        private Offsets() {
            throw new UnsupportedOperationException();
        }

        // Stream offsets table
        public static final String STREAM_OFFSETS_TABLE = "crypto_scout.stream_offsets";

        // Stream offsets
        static final String STREAM_OFFSETS_UPSERT = "INSERT INTO crypto_scout.stream_offsets(stream, \"offset\") VALUES " +
                "(?, ?) ON CONFLICT (stream) DO UPDATE SET \"offset\" = EXCLUDED.\"offset\", updated_at = NOW()";
        static final String STREAM_OFFSETS_SELECT = "SELECT \"offset\" FROM " + STREAM_OFFSETS_TABLE +
                " WHERE stream = ?";
        static final int CURRENT_OFFSET = 1;
        static final int STREAM = 1;
        static final int LAST_OFFSET = 2;
    }
}
