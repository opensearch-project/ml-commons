/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLCreateEventResponse extends ActionResponse implements ToXContentObject {

    private String eventId;  // Renamed from shorTermMemoryId
    private String sessionId;
    private String shorTermMemoryId;

    @Builder
    public MLCreateEventResponse(String eventId, String sessionId) {
        this.eventId = eventId;
        this.sessionId = sessionId;
        this.shorTermMemoryId = shorTermMemoryId;
    }

    public MLCreateEventResponse(StreamInput in) throws IOException {
        super(in);
        this.eventId = in.readOptionalString();
        this.sessionId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(eventId);
        out.writeOptionalString(sessionId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (eventId != null) {
            builder.field(EVENT_ID_FIELD, eventId);
        }
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        builder.endObject();
        return builder;
    }
}
