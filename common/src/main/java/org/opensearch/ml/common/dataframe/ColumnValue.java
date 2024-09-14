/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

public interface ColumnValue extends Writeable, ToXContentObject {

    ColumnType columnType();

    Object getValue();

    default short shortValue() {
        throw new RuntimeException("the value isn't Short type");
    }

    default int intValue() {
        throw new RuntimeException("the value isn't Integer type");
    }

    default long longValue() {
        throw new RuntimeException("the value isn't Long type");
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
