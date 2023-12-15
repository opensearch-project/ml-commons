/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.Interaction;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Action Response for get traces for an interaction
 */
@AllArgsConstructor
public class GetTracesResponse extends ActionResponse implements ToXContentObject {
    @Getter
    private List<Interaction> traces;
    @Getter
    private int nextToken;
    private boolean hasMoreTokens;

    /**
     * Constructor
     * @param in stream input; assumes GetTracesResponse.writeTo was called
     * @throws IOException if there's not a G.I.R. in the stream
     */
    public GetTracesResponse(StreamInput in) throws IOException {
        super(in);
        traces = in.readList(Interaction::fromStream);
        nextToken = in.readInt();
        hasMoreTokens = in.readBoolean();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(traces);
        out.writeInt(nextToken);
        out.writeBoolean(hasMoreTokens);
    }

    /**
     * Are there more pages in this search results
     * @return whether there are more traces in this search
     */
    public boolean hasMorePages() {
        return hasMoreTokens;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.startArray(ActionConstants.RESPONSE_TRACES_LIST_FIELD);
        for (Interaction trace : traces) {
            trace.toXContent(builder, params);
        }
        builder.endArray();
        if (hasMoreTokens) {
            builder.field(ActionConstants.NEXT_TOKEN_FIELD, nextToken);
        }
        builder.endObject();
        return builder;
    }

}
