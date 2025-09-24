/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

/**
 * Request to get an event from a memory container
 */
@Getter
public class MLGetEventRequest extends ActionRequest {

    private String memoryContainerId;
    private String eventId;

    public MLGetEventRequest(String memoryContainerId, String eventId) {
        this.memoryContainerId = memoryContainerId;
        this.eventId = eventId;
    }

    public MLGetEventRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.eventId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        out.writeString(eventId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (memoryContainerId == null || memoryContainerId.isEmpty()) {
            exception = new ActionRequestValidationException();
            exception.addValidationError("Memory container id is required");
        }

        if (eventId == null || eventId.isEmpty()) {
            if (exception == null) {
                exception = new ActionRequestValidationException();
            }
            exception.addValidationError("Event id is required");
        }

        return exception;
    }
}
