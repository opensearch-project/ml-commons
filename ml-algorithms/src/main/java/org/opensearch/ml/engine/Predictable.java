/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.engine.encryptor.Encryptor;

import java.util.Map;

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
    MLOutput predict(MLInput mlInput);

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
}
