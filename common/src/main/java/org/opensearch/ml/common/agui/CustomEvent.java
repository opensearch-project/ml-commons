/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomEvent extends BaseEvent {

    public static final String TYPE = "Custom";

    private String name;
    private Object value;

    public CustomEvent(String name, Object value) {
        super(TYPE, System.currentTimeMillis(), null);
        this.name = name;
        this.value = value;
    }

    public CustomEvent(StreamInput input) throws IOException {
        super(input);
        this.name = input.readString();
        if (input.readBoolean()) {
            this.value = input.readGenericValue();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        if (value != null) {
            out.writeBoolean(true);
            out.writeGenericValue(value);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("name", name);
        if (value != null) {
            builder.field("value", value);
        }
    }
}
