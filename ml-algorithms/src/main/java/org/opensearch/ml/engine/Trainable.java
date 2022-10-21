/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;

/**
 * This is machine learning algorithms train interface.
 */
public interface Trainable {

    /**
     * Train model with given features.
     * @param mlInput training data
     * @return ML model with serialized model content
     */
    MLModel train(MLInput mlInput);

}
