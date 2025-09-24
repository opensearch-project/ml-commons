/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class MLCreateEventRequest extends ActionRequest {

    MLCreateEventInput mlCreateEventInput;

    @Builder
    public MLCreateEventRequest(MLCreateEventInput mlCreateEventInput) {
        this.mlCreateEventInput = mlCreateEventInput;
    }

    public MLCreateEventRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreateEventInput = new MLCreateEventInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreateEventInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlCreateEventInput == null) {
            exception = addValidationError("ML create event input can't be null", exception);
        }
        return exception;
    }
}
