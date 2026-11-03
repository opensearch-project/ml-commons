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

/**
 * Request for semantic search on long-term memories
 */
@Getter
@Setter
@Builder
public class MLSemanticSearchMemoriesRequest extends ActionRequest {

    private MLSemanticSearchMemoriesInput mlSemanticSearchMemoriesInput;
    private String tenantId;

    public MLSemanticSearchMemoriesRequest(MLSemanticSearchMemoriesInput mlSemanticSearchMemoriesInput, String tenantId) {
        this.mlSemanticSearchMemoriesInput = mlSemanticSearchMemoriesInput;
        this.tenantId = tenantId;
    }

    public MLSemanticSearchMemoriesRequest(StreamInput in) throws IOException {
        super(in);
        this.mlSemanticSearchMemoriesInput = new MLSemanticSearchMemoriesInput(in);
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlSemanticSearchMemoriesInput.writeTo(out);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlSemanticSearchMemoriesInput == null) {
            exception = addValidationError("Semantic search memories input can't be null", exception);
        }
        return exception;
    }

    public static MLSemanticSearchMemoriesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLSemanticSearchMemoriesRequest) {
            return (MLSemanticSearchMemoriesRequest) actionRequest;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLSemanticSearchMemoriesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLSemanticSearchMemoriesRequest", e);
        }
    }
}
