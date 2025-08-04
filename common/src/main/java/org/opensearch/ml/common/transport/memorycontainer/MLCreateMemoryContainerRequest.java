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

@Getter
public class MLCreateMemoryContainerRequest extends ActionRequest {

    private final MLCreateMemoryContainerInput mlCreateMemoryContainerInput;

    @Builder
    public MLCreateMemoryContainerRequest(MLCreateMemoryContainerInput mlCreateMemoryContainerInput) {
        this.mlCreateMemoryContainerInput = mlCreateMemoryContainerInput;
    }

    public MLCreateMemoryContainerRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreateMemoryContainerInput = new MLCreateMemoryContainerInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreateMemoryContainerInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (mlCreateMemoryContainerInput == null) {
            return addValidationError("Memory container input can't be null", null);
        }

        // All MemoryStorageConfig validation is handled by MemoryStorageConfig itself
        return null;
    }

    public static MLCreateMemoryContainerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateMemoryContainerRequest) {
            return (MLCreateMemoryContainerRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateMemoryContainerRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateMemoryContainerRequest", e);
        }
    }
}
