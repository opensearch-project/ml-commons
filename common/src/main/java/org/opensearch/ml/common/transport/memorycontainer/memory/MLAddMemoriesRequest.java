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
public class MLAddMemoriesRequest extends ActionRequest {

    MLAddMemoriesInput mlAddMemoryInput;

    @Builder
    public MLAddMemoriesRequest(MLAddMemoriesInput mlAddMemoryInput) {
        this.mlAddMemoryInput = mlAddMemoryInput;
    }

    public MLAddMemoriesRequest(StreamInput in) throws IOException {
        super(in);
        this.mlAddMemoryInput = new MLAddMemoriesInput(in);
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
            exception = addValidationError("ML add memory input can't be null", exception);
        }
        return exception;
    }
}
