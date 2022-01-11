/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.Model;

/**
 * This is machine learning algorithms predict interface.
 */
public interface Predictable {

    /**
     * Predict with given features and model (optional).
     * @param dataFrame features data
     * @param model the java serialized model
     * @return predicted results
     */
    MLOutput predict(DataFrame dataFrame, Model model);

}
