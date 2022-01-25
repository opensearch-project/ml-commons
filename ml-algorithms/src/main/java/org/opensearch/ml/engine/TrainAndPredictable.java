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
import org.opensearch.ml.common.parameter.MLOutput;


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
