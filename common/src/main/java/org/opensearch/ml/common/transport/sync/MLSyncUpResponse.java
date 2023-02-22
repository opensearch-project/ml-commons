/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import lombok.Getter;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLSyncUpResponse extends ActionResponse implements ToXContentObject {
    public static final String STATUS_FIELD = "status";

    private String status;

    public MLSyncUpResponse(StreamInput in) throws IOException {
        super(in);
        this.status = in.readString();
    }

    public MLSyncUpResponse(String status) {
        this.status= status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
