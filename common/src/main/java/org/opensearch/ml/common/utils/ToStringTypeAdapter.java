/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ToStringTypeAdapter<T> extends TypeAdapter<T> {

    private final Class<T> clazz;

    public ToStringTypeAdapter(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        String json = value.toString();
        out.jsonValue(json);
    }

    @Override
    public T read(JsonReader in) throws IOException {
        throw new UnsupportedOperationException("Deserialization not supported");
    }
}
