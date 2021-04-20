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
    DataFrame predict(DataFrame dataFrame, String model);

    /**
     * Train model with given features.
     * @param dataFrame training data
     * @return the java serialized model
     */
    Model train(DataFrame dataFrame);
}
