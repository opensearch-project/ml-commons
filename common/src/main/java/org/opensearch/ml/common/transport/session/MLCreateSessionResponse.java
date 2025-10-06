/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MLCreateSessionResponse extends ActionResponse implements ToXContentObject {

    private static final String SESSION_ID_FIELD = "session_id";
    private static final String STATUS_FIELD = "status";

    private String sessionId;
    private String status;

    public MLCreateSessionResponse(String sessionId, String status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    public MLCreateSessionResponse(StreamInput in) throws IOException {
        super(in);
        this.sessionId = in.readString();
        this.status = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(sessionId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SESSION_ID_FIELD, sessionId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
