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

import java.io.IOException;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.xcontent.XContentBuilder;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
@Builder
@RequiredArgsConstructor
@ToString
public class ColumnMeta implements Writeable {
    String name;
    ColumnType columnType;

    ColumnMeta(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        this.columnType = in.readEnum(ColumnType.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeEnum(columnType);
    }

    public void toXContent(final XContentBuilder builder) throws IOException {
        builder.startObject();
        builder.field("name", name);
        builder.field("column_type", columnType);
        builder.endObject();
    }
}
