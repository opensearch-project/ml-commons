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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataframe.ColumnType;

import java.io.IOException;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
@Builder
@RequiredArgsConstructor
@ToString
public class MLAlgoParamMeta implements ToXContentObject, Writeable {
    String name;
    //TODO: rename ColumnType as DataType
    ColumnType columnType;
    boolean isOptional; // true means the param is optional

    MLAlgoParamMeta(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        this.columnType = in.readEnum(ColumnType.class);
        this.isOptional = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeEnum(columnType);
        out.writeBoolean(isOptional);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("name", name);
        builder.field("column_type", columnType);
        builder.field("is_optional", isOptional);
        builder.endObject();
        return builder;
    }
}
