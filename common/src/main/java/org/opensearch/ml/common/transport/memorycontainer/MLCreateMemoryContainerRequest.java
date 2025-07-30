/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INVALID_MODEL_TYPE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR;

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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.SemanticStorageConfig;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLCreateMemoryContainerRequest extends ActionRequest {

    private MLCreateMemoryContainerInput mlCreateMemoryContainerInput;

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

        ActionRequestValidationException exception = null;
        SemanticStorageConfig semanticStorage = mlCreateMemoryContainerInput.getSemanticStorage();

        if (semanticStorage != null && semanticStorage.isSemanticStorageEnabled()) {
            if (semanticStorage.getModelType() == null) {
                exception = addValidationError(SEMANTIC_STORAGE_MODEL_TYPE_REQUIRED_ERROR, exception);
            } else {
                FunctionName modelType = semanticStorage.getModelType();
                if (modelType != FunctionName.TEXT_EMBEDDING && modelType != FunctionName.SPARSE_ENCODING) {
                    exception = addValidationError(INVALID_MODEL_TYPE_ERROR, exception);
                }

                // Model ID is required when semantic storage is enabled
                if (semanticStorage.getModelId() == null) {
                    exception = addValidationError(SEMANTIC_STORAGE_MODEL_ID_REQUIRED_ERROR, exception);
                }

                // Validate dimension requirements
                if (modelType == FunctionName.TEXT_EMBEDDING) {
                    // Dimension is required for TEXT_EMBEDDING
                    if (semanticStorage.getDimension() == null) {
                        exception = addValidationError(TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR, exception);
                    }
                } else if (modelType == FunctionName.SPARSE_ENCODING) {
                    // Dimension is not allowed for sparse encoding
                    if (semanticStorage.getDimension() != null) {
                        exception = addValidationError(SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR, exception);
                    }
                }
            }
        }

        return exception;
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
