/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.parameter;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Parameter class for ML algorithms. This class doesn't check the the type of value. Instead, it will be only initialized
 * by MLParameterBuilder class which has all supported types of value.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLParameter implements Writeable {
    String name;
    Object value;

    public MLParameter(String name, Object value) {
        if(Objects.isNull(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException("name or value can't be null");
        }

        this.name = name;
        this.value = value;
    }

    public MLParameter(StreamInput input) throws IOException {
        this.name = input.readString();
        this.value = input.readGenericValue();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeGenericValue(value);
    }
}
