/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_MISMATCH_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_FOUND_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_MODEL_NOT_REMOTE_ERROR;

import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for validating ML models used in memory containers.
 * Provides reusable validation logic for LLM and embedding models.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MemoryContainerModelValidator {

    /**
     * Validates that the LLM model exists and is of REMOTE type.
     *
     * @param tenantId      the tenant id. This is necessary for multi-tenancy.
     * @param llmId         The LLM model ID to validate
     * @param modelManager  The ML model manager
     * @param client        The OpenSearch client
     * @param listener      Action listener that receives true on success, or error on failure
     */
    public static void validateLlmModel(
        String tenantId,
        String llmId,
        MLModelManager modelManager,
        Client client,
        ActionListener<Boolean> listener
    ) {
        if (llmId == null) {
            listener.onResponse(true);
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(llmModel -> {
                if (llmModel.getAlgorithm() != FunctionName.REMOTE) {
                    listener.onFailure(new IllegalArgumentException(String.format(LLM_MODEL_NOT_REMOTE_ERROR, llmModel.getAlgorithm())));
                    return;
                }
                listener.onResponse(true);
            }, e -> {
                log.error("Failed to get LLM model: {}", llmId, e);
                listener.onFailure(new IllegalArgumentException(String.format(LLM_MODEL_NOT_FOUND_ERROR, llmId)));
            }), context::restore);

            modelManager.getModel(llmId, tenantId, wrappedListener);
        }
    }

    /**
     * Validates that the embedding model exists and its type matches the expected type.
     *
     * @param embeddingModelId  The embedding model ID to validate
     * @param expectedType      The expected FunctionName type (TEXT_EMBEDDING or SPARSE_ENCODING)
     * @param modelManager      The ML model manager
     * @param client            The OpenSearch client
     * @param listener          Action listener that receives true on success, or error on failure
     */
    public static void validateEmbeddingModel(
        String tenantId,
        String embeddingModelId,
        FunctionName expectedType,
        MLModelManager modelManager,
        Client client,
        ActionListener<Boolean> listener
    ) {
        if (embeddingModelId == null) {
            listener.onResponse(true);
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(embeddingModel -> {
                FunctionName modelAlgorithm = embeddingModel.getAlgorithm();

                // Model must be either the expected type or REMOTE
                if (modelAlgorithm != expectedType && modelAlgorithm != FunctionName.REMOTE) {
                    listener
                        .onFailure(
                            new IllegalArgumentException(String.format(EMBEDDING_MODEL_TYPE_MISMATCH_ERROR, expectedType, modelAlgorithm))
                        );
                    return;
                }

                listener.onResponse(true);
            }, e -> {
                log.error("Failed to get embedding model: {}", embeddingModelId, e);
                listener.onFailure(new IllegalArgumentException(String.format(EMBEDDING_MODEL_NOT_FOUND_ERROR, embeddingModelId)));
            }), context::restore);

            modelManager.getModel(embeddingModelId, tenantId, wrappedListener);
        }
    }
}
