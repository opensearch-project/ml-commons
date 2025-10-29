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
public class MLUpdateMemoryContainerRequest extends ActionRequest {
    @Setter
    private MLUpdateMemoryContainerInput mlUpdateMemoryContainerInput;
    private String memoryContainerId;

    @Builder
    public MLUpdateMemoryContainerRequest(String memoryContainerId, MLUpdateMemoryContainerInput mlUpdateMemoryContainerInput) {
        this.memoryContainerId = memoryContainerId;
        this.mlUpdateMemoryContainerInput = mlUpdateMemoryContainerInput;
    }

    public MLUpdateMemoryContainerRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.mlUpdateMemoryContainerInput = new MLUpdateMemoryContainerInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        mlUpdateMemoryContainerInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlUpdateMemoryContainerInput == null) {
            exception = addValidationError("Update memory container input can't be null", exception);
        }
        if (memoryContainerId == null) {
            exception = addValidationError("Memory container id can't be null", exception);
        }
        return exception;
    }

    public static MLUpdateMemoryContainerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateMemoryContainerRequest) {
            return (MLUpdateMemoryContainerRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateMemoryContainerRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLUpdateMemoryContainerRequest", e);
        }
    }

}
