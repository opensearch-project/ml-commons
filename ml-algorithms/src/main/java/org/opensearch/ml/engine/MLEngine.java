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
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.engine.clustering.KMeans;
import org.opensearch.ml.engine.contants.MLAlgoNames;
import org.opensearch.ml.engine.regression.LinearRegression;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {
    public static DataFrame predict(String algoName, List<MLParameter> parameters, DataFrame dataFrame, Model model) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        switch (algoName.trim().toLowerCase()) {
            case MLAlgoNames.KMEANS:
                KMeans kMeans = new KMeans(parameters);
                return kMeans.predict(dataFrame, model);
            case MLAlgoNames.LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression(parameters);
                return linearRegression.predict(dataFrame, model);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }

    public static Model train(String algoName, List<MLParameter> parameters, DataFrame dataFrame) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        switch (algoName.trim().toLowerCase()) {
            case MLAlgoNames.KMEANS:
                KMeans kMeans = new KMeans(parameters);
                return kMeans.train(dataFrame);
            case MLAlgoNames.LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression(parameters);
                return linearRegression.train(dataFrame);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }
}
