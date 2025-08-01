/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

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

public class MLMemoryContainerDeleteRequest extends ActionRequest {
    @Getter
    String memoryContainerId;

    @Getter
    String tenantId;

    @Builder
    public MLMemoryContainerDeleteRequest(String memoryContainerId, String tenantId) {
        this.memoryContainerId = memoryContainerId;
        this.tenantId = tenantId;
    }

    public MLMemoryContainerDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.memoryContainerId = input.readString();
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(memoryContainerId);
        output.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.memoryContainerId == null) {
            exception = addValidationError("ML memory container id can't be null", exception);
        }

        return exception;
    }

    public static MLMemoryContainerDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMemoryContainerDeleteRequest) {
            return (MLMemoryContainerDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryContainerDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMemoryContainerDeleteRequest", e);
        }
    }
}
