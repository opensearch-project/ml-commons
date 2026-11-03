/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import java.util.Optional;

import org.opensearch.ml.common.FunctionName;

/**
 * Interface for model management operations needed by gRPC services.
 * This abstracts the model manager to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLModelManager {

    /**
     * Gets the function name for a model if it exists in cache.
     *
     * @param modelId the model ID
     * @return Optional containing the function name if cached, empty otherwise
     */
    Optional<FunctionName> getOptionalModelFunctionName(String modelId);
}
