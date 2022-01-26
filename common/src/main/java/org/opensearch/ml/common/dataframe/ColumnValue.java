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

package org.opensearch.ml.common.dataframe;

import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;

public interface ColumnValue extends Writeable, ToXContentObject {

    ColumnType columnType();

    Object getValue();

    default int intValue() {
        throw new RuntimeException("the value isn't Integer type");
    }

    default String stringValue() {
        throw new RuntimeException("the value isn't String type");
    }

    default double doubleValue() {
        throw new RuntimeException("the value isn't Double type");
    }

    default float floatValue() {
        throw new RuntimeException("the value isn't Float type");
    }

    default boolean booleanValue() {
        throw new RuntimeException("the value isn't Boolean type");
    }

    default void toXContent(XContentBuilder builder) throws IOException {
        toXContent(builder, EMPTY_PARAMS);
    }

    @Override
    default XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("column_type", columnType());
        if (columnType() != ColumnType.NULL) {
            builder.field("value", getValue());
        }
        builder.endObject();
        return builder;
    }

    default boolean equals(ColumnValue other) {
        if (this == other) {
            return true;
        }
        if (this.columnType() != other.columnType()) {
            return false;
        }
        return Objects.equals(this.getValue(), other.getValue());
    }
}
