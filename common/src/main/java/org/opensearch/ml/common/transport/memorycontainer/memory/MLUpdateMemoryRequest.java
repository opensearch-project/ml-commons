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
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
public class MLUpdateMemoryRequest extends ActionRequest {
    @Setter
    private MLUpdateMemoryInput mlUpdateMemoryInput;
    private String memoryContainerId;
    private MemoryType memoryType;
    private String memoryId;
    private String tenantId;

    @Builder
    public MLUpdateMemoryRequest(
        String memoryContainerId,
        MemoryType memoryType,
        String memoryId,
        MLUpdateMemoryInput mlUpdateMemoryInput,
        String tenantId
    ) {
        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType;
        this.memoryId = memoryId;
        this.mlUpdateMemoryInput = mlUpdateMemoryInput;
        this.tenantId = tenantId;
    }

    public MLUpdateMemoryRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.memoryType = in.readEnum(MemoryType.class);
        this.memoryId = in.readString();
        this.mlUpdateMemoryInput = new MLUpdateMemoryInput(in);
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(memoryContainerId);
        out.writeEnum(memoryType);
        out.writeString(memoryId);
        mlUpdateMemoryInput.writeTo(out);
        out.writeOptionalString(tenantId);
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
        if (memoryType == null) {
            exception = addValidationError("Memory type can't be null", exception);
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
