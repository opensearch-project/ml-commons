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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class MLAddMemoryRequest extends ActionRequest {

    MLAddMemoryInput mlAddMemoryInput;

    @Builder
    public MLAddMemoryRequest(MLAddMemoryInput mlAddMemoryInput) {
        this.mlAddMemoryInput = mlAddMemoryInput;
    }

    public MLAddMemoryRequest(StreamInput in) throws IOException {
        super(in);
        this.mlAddMemoryInput = new MLAddMemoryInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlAddMemoryInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlAddMemoryInput == null) {
            exception = new ActionRequestValidationException();
            exception.addValidationError("ML add memory input can't be null");
        }
        return exception;
    }
}
