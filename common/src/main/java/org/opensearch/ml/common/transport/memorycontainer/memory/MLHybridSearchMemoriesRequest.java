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
@Setter
@Builder
public class MLHybridSearchMemoriesRequest extends ActionRequest {

    private MLHybridSearchMemoriesInput mlHybridSearchMemoriesInput;
    private String tenantId;

    public MLHybridSearchMemoriesRequest(MLHybridSearchMemoriesInput mlHybridSearchMemoriesInput, String tenantId) {
        this.mlHybridSearchMemoriesInput = mlHybridSearchMemoriesInput;
        this.tenantId = tenantId;
    }

    public MLHybridSearchMemoriesRequest(StreamInput in) throws IOException {
        super(in);
        this.mlHybridSearchMemoriesInput = new MLHybridSearchMemoriesInput(in);
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlHybridSearchMemoriesInput.writeTo(out);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlHybridSearchMemoriesInput == null) {
            exception = addValidationError("Hybrid search memories input can't be null", exception);
        }
        return exception;
    }

    public static MLHybridSearchMemoriesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLHybridSearchMemoriesRequest) {
            return (MLHybridSearchMemoriesRequest) actionRequest;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLHybridSearchMemoriesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLHybridSearchMemoriesRequest", e);
        }
    }
}
