/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.MLInputDataset;

/**
 * This is machine learning algorithms train interface.
 */
public interface Trainable {

    /**
     * Train model with given features.
     * @param inputDataset training data
     * @return ML model with serialized model content
     */
    MLModel train(MLInputDataset inputDataset);

}
