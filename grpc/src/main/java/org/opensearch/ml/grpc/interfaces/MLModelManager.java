/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import java.util.Optional;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;

/**
 * Interface for model management operations needed by gRPC services.
 * This abstracts the model manager to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLModelManager {

    /**
     * Gets a model by ID with optional tenant filtering.
     *
     * @param modelId the model ID to retrieve
     * @param tenantId optional tenant ID for multi-tenancy (null if disabled)
     * @param listener callback for async result
     */
    void getModel(String modelId, String tenantId, ActionListener<MLModel> listener);

    /**
     * Gets the function name for a model if it exists in cache.
     *
     * @param modelId the model ID
     * @return Optional containing the function name if cached, empty otherwise
     */
    Optional<FunctionName> getOptionalModelFunctionName(String modelId);
}
