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

package org.opensearch.ml.engine.clustering;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.Model;

public class KMeans implements MLAlgo {
    @Override
    public DataFrame predict(DataFrame dataFrame, String model) {
        return null;
    }

    @Override
    public Model train(DataFrame dataFrame) {
        return null;
    }
}
