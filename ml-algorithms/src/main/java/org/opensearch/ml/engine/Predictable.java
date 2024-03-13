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

/**
 * This is machine learning algorithms predict interface.
 */
public interface Predictable {

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
        throw new IllegalStateException("Method is not implemented");
    }

    default void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        actionListener.onFailure(new IllegalStateException("Method is not implemented"));
    }

    /**
     * Init model (load model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     * @param encryptor encryptor
     */
    void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor);

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
