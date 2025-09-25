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

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLDeleteWorkingMemoryRequest extends ActionRequest {
    private String memoryContainerId;
    private String workingMemoryId;

    @Builder
    public MLDeleteWorkingMemoryRequest(String memoryContainerId, String workingMemoryId) {
        this.memoryContainerId = memoryContainerId;
        this.workingMemoryId = workingMemoryId;
    }

    public MLDeleteWorkingMemoryRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.workingMemoryId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        out.writeString(workingMemoryId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (memoryContainerId == null || memoryContainerId.isEmpty()) {
            exception = new ActionRequestValidationException();
            exception.addValidationError("Memory container id is required");
        }
        if (workingMemoryId == null || workingMemoryId.isEmpty()) {
            if (exception == null) {
                exception = new ActionRequestValidationException();
            }
            exception.addValidationError("Working memory id is required");
        }
        return exception;
    }
}
