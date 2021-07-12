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
import org.opensearch.ml.engine.algorithms.custom.PMMLModel;
import org.opensearch.ml.engine.annotation.MLAlgorithm;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;
import org.opensearch.ml.engine.contants.MLAlgoNames;
import org.opensearch.ml.engine.exceptions.MetaDataException;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {
    private static final String ALGO_PACKAGE_NAME = "org.opensearch.ml.engine.algorithms";

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
            case MLAlgoNames.PMML:
                PMMLModel pmmlModel = new PMMLModel();
                return pmmlModel.predict(dataFrame, model);
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
            case MLAlgoNames.PMML:
                throw new IllegalArgumentException("Can't re-train uploaded custom models.");
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }

    public static MLEngineMetaData getMetaData() {
        MLEngineMetaData engineMetaData = new MLEngineMetaData();
        Reflections reflections = new Reflections(ALGO_PACKAGE_NAME);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLAlgorithm.class);
        try {
            for (Class c : classes) {
                MLAlgo algo = (MLAlgo) c.getDeclaredConstructor().newInstance();
                engineMetaData.addAlgoMetaData(algo.getMetaData());
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new MetaDataException("Failed to get ML engine meta data.", e.getCause());
        }
        return engineMetaData;
    }
}
