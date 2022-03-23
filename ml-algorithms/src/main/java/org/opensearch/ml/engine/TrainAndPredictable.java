/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.output.MLOutput;


/**
 * This is machine learning algorithms train interface.
 */
public interface TrainAndPredictable extends Trainable, Predictable {

    /**
     * Train model with given features. Then predict with the same data.
     * @param dataFrame training data
     * @return the java serialized model
     */
    MLOutput trainAndPredict(DataFrame dataFrame);

}
