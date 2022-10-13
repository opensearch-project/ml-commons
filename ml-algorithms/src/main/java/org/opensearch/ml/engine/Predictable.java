/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.output.MLOutput;

import java.util.Map;

/**
 * This is machine learning algorithms predict interface.
 */
public interface Predictable {

    /**
     * Predict with given input data and model (optional).
     * Will reload model into memory with model content.
     * @param inputDataset input data set
     * @param model the java serialized model
     * @return predicted results
     */
    MLOutput predict(MLInputDataset inputDataset, MLModel model);

    /**
     * Predict with given input data with loaded model.
     * @param inputDataset input data set
     * @return predicted results
     */
    MLOutput predict(MLInputDataset inputDataset);

    /**
     * Init model (load model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     */
    void initModel(MLModel model, Map<String, Object> params);

    /**
     * Close resources like loaded model.
     */
    void close();
}
