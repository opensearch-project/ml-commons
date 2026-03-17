/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.transport.TransportChannel;

/**
 * This is machine learning algorithms predict interface.
 */
public interface Predictable {

    String METHOD_NOT_IMPLEMENTED_ERROR_MSG = "Method is not implemented";

    /**
     * Predict with given input data and model.
     * Will reload model into memory with model content.
     * @param mlInput input data
     * @param model the java serialized model
     * @return predicted results
     */
    MLOutput predict(MLInput mlInput, MLModel model);

    /**
     * Predict with given input data for deployed model.
     * @param mlInput input data
     * @return predicted results
     */
    default MLOutput predict(MLInput mlInput) {
        throw new IllegalStateException(METHOD_NOT_IMPLEMENTED_ERROR_MSG);
    }

    default void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        asyncPredict(mlInput, actionListener, null);
    }

    default void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener, TransportChannel channel) {
        actionListener.onFailure(new IllegalStateException(METHOD_NOT_IMPLEMENTED_ERROR_MSG));
    }

    /**
     * Init model (load model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     * @param encryptor encryptor
     */
    default void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        throw new IllegalStateException(METHOD_NOT_IMPLEMENTED_ERROR_MSG);
    }

    default void initModelAsync(MLModel model, Map<String, Object> params, Encryptor encryptor, ActionListener<Predictable> listener) {
        throw new IllegalStateException(METHOD_NOT_IMPLEMENTED_ERROR_MSG);
    }

    /**
     * Close resources like deployed model.
     */
    void close();

    /**
     * Check if model ready to be used.
     * @return
     */
    boolean isModelReady();
}
