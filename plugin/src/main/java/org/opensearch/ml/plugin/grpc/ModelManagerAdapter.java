/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import java.util.Optional;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.grpc.interfaces.MLModelManager;

/**
 * Adapter that wraps the plugin's MLModelManager to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 */
public class ModelManagerAdapter implements MLModelManager {

    private final org.opensearch.ml.model.MLModelManager delegate;

    public ModelManagerAdapter(org.opensearch.ml.model.MLModelManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<FunctionName> getOptionalModelFunctionName(String modelId) {
        return delegate.getOptionalModelFunctionName(modelId);
    }
}
