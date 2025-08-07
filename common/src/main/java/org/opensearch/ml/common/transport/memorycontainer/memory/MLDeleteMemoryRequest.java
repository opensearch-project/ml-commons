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

@Getter
public class MLDeleteMemoryRequest extends ActionRequest {
    private final String memoryContainerId;
    private final String memoryId;

    @Builder
    public MLDeleteMemoryRequest(String memoryContainerId, String memoryId) {
        this.memoryContainerId = memoryContainerId;
        this.memoryId = memoryId;
    }

    public MLDeleteMemoryRequest(StreamInput input) throws IOException {
        super(input);
        this.memoryContainerId = input.readString();
        this.memoryId = input.readString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(memoryContainerId);
        output.writeString(memoryId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.memoryContainerId == null) {
            exception = addValidationError("Memory container id can't be null", exception);
        }

        if (this.memoryId == null) {
            exception = addValidationError("Memory id can't be null", exception);
        }

        return exception;
    }

    public static MLDeleteMemoryRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLDeleteMemoryRequest) {
            return (MLDeleteMemoryRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLDeleteMemoryRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLDeleteMemoryRequest", e);
        }
    }
}
