/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLOutput;


/**
 * This is machine learning algorithms interface.
 */
public interface MLAlgo {

    /**
     * Predict with given features and model (optional).
     * @param dataFrame features data
     * @param model the java serialized model
     * @return predicted results
     */
    default MLOutput predict(DataFrame dataFrame, Model model) {
        throw new RuntimeException("Unsupported predict.");
    }

    /**
     * Execute algorithm with given params and input data frame.
     * @param params ML algorithm parameter
     * @param dataFrame input data frame
     * @return execution result
     */
    default MLOutput execute(MLAlgoParams params, DataFrame dataFrame) {
        throw new RuntimeException("Unsupported execute.");
    }

    /**
     * Train model with given features.
     * @param dataFrame training data
     * @return the java serialized model
     */
    default Model train(DataFrame dataFrame) {
        throw new RuntimeException("Unsupported train.");
    }

    /**
     * Return meta data of the algorithm.
     * @return the meta data
     */
    default MLAlgoMetaData getMetaData() {
        throw new RuntimeException("Unsupported getMetaData.");
    }
}
