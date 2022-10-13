/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.output.MLOutput;


/**
 * This is machine learning algorithms train and predict interface.
 */
public interface TrainAndPredictable extends Trainable, Predictable {

    /**
     * Train model with given input data. Then predict with the same data.
     * @param inputDataset training data
     * @return ML model with serialized model content
     */
    MLOutput trainAndPredict(MLInputDataset inputDataset);

}
