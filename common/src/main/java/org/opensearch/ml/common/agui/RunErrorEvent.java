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
public class RunErrorEvent extends BaseEvent {

    public static final String TYPE = "RUN_ERROR";

    private String message;
    private String code;

    public RunErrorEvent(String message, String code) {
        super(TYPE, System.currentTimeMillis(), null);
        this.message = message != null ? message : "";
        this.code = code;
    }

    public RunErrorEvent(StreamInput input) throws IOException {
        super(input);
        this.message = input.readString();
        this.code = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(message);
        out.writeOptionalString(code);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("message", message);
        if (code != null) {
            builder.field("code", code);
        }
    }
}
