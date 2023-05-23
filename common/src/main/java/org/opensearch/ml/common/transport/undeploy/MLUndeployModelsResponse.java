/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import lombok.Getter;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLUndeployModelsResponse extends ActionResponse implements ToXContentObject {
    private MLUndeployModelNodesResponse response;

    public MLUndeployModelsResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            this.response = new MLUndeployModelNodesResponse(in);
        }
    }

    public MLUndeployModelsResponse(MLUndeployModelNodesResponse response) {
        this.response = response;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (this.response != null) {
            out.writeBoolean(true);
            this.response.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (response != null) {
            response.toXContent(builder, params);
        } else {
            builder.startObject();
            builder.endObject();
        }
        return builder;
    }
}
