/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.Getter;

/**
 * ActionRequest for get traces
 */
public class GetTracesRequest extends ActionRequest {
    @Getter
    private String interactionId;
    @Getter
    private int maxResults = ActionConstants.DEFAULT_MAX_RESULTS;
    @Getter
    private int from = 0;

    /**
     * Constructor
     * @param interactionId UID of the interaction to get traces from
     */
    public GetTracesRequest(String interactionId) {
        this.interactionId = interactionId;
    }

    /**
     * Constructor
     * @param interactionId UID of the conversation to get interactions from
     * @param maxResults number of interactions to retrieve
     */
    public GetTracesRequest(String interactionId, int maxResults) {
        this.interactionId = interactionId;
        this.maxResults = maxResults;
    }

    /**
     * Constructor
     * @param interactionId UID of the conversation to get interactions from
     * @param maxResults number of interactions to retrieve
     * @param from position of first interaction to retrieve
     */
    public GetTracesRequest(String interactionId, int maxResults, int from) {
        this.interactionId = interactionId;
        this.maxResults = maxResults;
        this.from = from;
    }

    /**
     * Constructor
     * @param in streaminput to read this from. assumes there was a GetTracesRequest.writeTo
     * @throws IOException if there wasn't a GIR in the stream
     */
    public GetTracesRequest(StreamInput in) throws IOException {
        super(in);
        this.interactionId = in.readString();
        this.maxResults = in.readInt();
        this.from = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(interactionId);
        out.writeInt(maxResults);
        out.writeInt(from);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (interactionId == null) {
            exception = addValidationError("Traces must be retrieved from an interaction", exception);
        }
        if (maxResults <= 0) {
            exception = addValidationError("The number of traces to retrieve must be positive", exception);
        }
        if (from < 0) {
            exception = addValidationError("The starting position must be nonnegative", exception);
        }

        return exception;
    }

    /**
     * Makes a GetTracesRequest out of a RestRequest
     * @param request Rest Request representing a get traces request
     * @return a new GetTracesRequest
     * @throws IOException if something goes wrong
     */
    public static GetTracesRequest fromRestRequest(RestRequest request) throws IOException {
        String cid = request.param(ActionConstants.RESPONSE_INTERACTION_ID_FIELD);
        if (request.hasParam(ActionConstants.NEXT_TOKEN_FIELD)) {
            int from = Integer.parseInt(request.param(ActionConstants.NEXT_TOKEN_FIELD));
            if (request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                int maxResults = Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD));
                return new GetTracesRequest(cid, maxResults, from);
            } else {
                return new GetTracesRequest(cid, ActionConstants.DEFAULT_MAX_RESULTS, from);
            }
        } else {
            if (request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                int maxResults = Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD));
                return new GetTracesRequest(cid, maxResults);
            } else {
                return new GetTracesRequest(cid);
            }
        }
    }

}
