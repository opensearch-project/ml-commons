/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport;

import java.io.IOException;
import java.util.UUID;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MLTaskRequest extends ActionRequest {

    protected boolean dispatchTask;
    protected final String requestID;

    public MLTaskRequest(boolean dispatchTask) {
        this.dispatchTask = dispatchTask;
        this.requestID = UUID.randomUUID().toString();
    }

    public MLTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.requestID = in.readString();
        this.dispatchTask = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(requestID);
        out.writeBoolean(dispatchTask);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
