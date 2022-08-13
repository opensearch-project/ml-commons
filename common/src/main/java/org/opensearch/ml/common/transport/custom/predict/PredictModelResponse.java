/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.predict;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class PredictModelResponse extends ActionResponse implements ToXContentObject {

    private String result;

    public PredictModelResponse(StreamInput in) throws IOException {
        super(in);
        this.result = in.readString();
    }

    public PredictModelResponse(String result) {
        this.result = result;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(result);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (result != null) {
            builder.field("result", result);
        }
        builder.endObject();
        return builder;
    }
}
