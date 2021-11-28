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
 * This is machine learning algorithms predict interface.
 */
public interface Predictable {

    /**
     * Predict with given features and model (optional).
     * @param dataFrame features data
     * @param model the java serialized model
     * @return predicted results
     */
    default MLOutput predict(DataFrame dataFrame, Model model) {
        throw new RuntimeException("Unsupported predict.");
    }

}
