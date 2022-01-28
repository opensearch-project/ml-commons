/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;


/**
 * This is machine learning algorithms train interface.
 */
public interface Trainable {

    /**
     * Train model with given features.
     * @param dataFrame training data
     * @return the java serialized model
     */
    Model train(DataFrame dataFrame);

}
