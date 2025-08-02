/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INVALID_EMBEDDING_MODEL_TYPE_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_LLM_MODEL_ID_REQUIRED_ERROR;
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
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

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
        MemoryStorageConfig memoryStorageConfig = mlCreateMemoryContainerInput.getMemoryStorageConfig();

        if (memoryStorageConfig != null && memoryStorageConfig.isSemanticStorageEnabled()) {
            if (memoryStorageConfig.getEmbeddingModelType() == null) {
                exception = addValidationError(SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR, exception);
            } else {
                FunctionName embeddingModelType = memoryStorageConfig.getEmbeddingModelType();
                if (embeddingModelType != FunctionName.TEXT_EMBEDDING && embeddingModelType != FunctionName.SPARSE_ENCODING) {
                    exception = addValidationError(INVALID_EMBEDDING_MODEL_TYPE_ERROR, exception);
                }

                // Model IDs are required when semantic storage is enabled
                if (memoryStorageConfig.getEmbeddingModelId() == null) {
                    exception = addValidationError(SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR, exception);
                }

                if (memoryStorageConfig.getLlmModelId() == null) {
                    exception = addValidationError(SEMANTIC_STORAGE_LLM_MODEL_ID_REQUIRED_ERROR, exception);
                }

                // Validate dimension requirements
                if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                    // Dimension is required for TEXT_EMBEDDING
                    if (memoryStorageConfig.getDimension() == null) {
                        exception = addValidationError(TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR, exception);
                    }
                } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                    // Dimension is not allowed for sparse encoding
                    if (memoryStorageConfig.getDimension() != null) {
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
