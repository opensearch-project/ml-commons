/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.AG_UI_OUTPUT)
public class AGUIOutput extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.AG_UI_OUTPUT;

    List<Object> events;

    @Override
    protected MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Builder
    public AGUIOutput(List<Object> events) {
        super(OUTPUT_TYPE);
        this.events = events;
    }

    public AGUIOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        if (in.readBoolean()) {
            this.events = in.readList(StreamInput::readGenericValue);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (events != null) {
            out.writeBoolean(true);
            out.writeCollection(events, StreamOutput::writeGenericValue);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        // Return the events array directly as the top-level response
        if (events != null) {
            builder.startArray();
            for (Object event : events) {
                builder.value(event);
            }
            builder.endArray();
        } else {
            builder.startArray().endArray();
        }
        return builder;
    }
}
