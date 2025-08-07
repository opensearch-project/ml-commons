/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
public class MLUpdateMemoryRequest extends ActionRequest {
    @Setter
    private MLUpdateMemoryInput mlUpdateMemoryInput;
    private String memoryContainerId;
    private String memoryId;

    @Builder
    public MLUpdateMemoryRequest(String memoryContainerId, String memoryId, MLUpdateMemoryInput mlUpdateMemoryInput) {
        this.memoryContainerId = memoryContainerId;
        this.memoryId = memoryId;
        this.mlUpdateMemoryInput = mlUpdateMemoryInput;
    }

    public MLUpdateMemoryRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.memoryId = in.readString();
        this.mlUpdateMemoryInput = new MLUpdateMemoryInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        out.writeString(memoryId);
        mlUpdateMemoryInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlUpdateMemoryInput == null) {
            exception = addValidationError("Update memory input can't be null", exception);
        }
        if (memoryContainerId == null) {
            exception = addValidationError("Memory container id can't be null", exception);
        }
        if (memoryId == null) {
            exception = addValidationError("Memory id can't be null", exception);
        }
        return exception;
    }

    public static MLUpdateMemoryRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateMemoryRequest) {
            return (MLUpdateMemoryRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateMemoryRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLUpdateMemoryRequest", e);
        }
    }
}
