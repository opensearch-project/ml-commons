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
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.LinearRegressionParams;
import org.opensearch.ml.common.parameter.MLAlgoName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.SampleAlgoParams;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
import org.opensearch.ml.engine.algorithms.sample.SampleAlgo;
import org.opensearch.ml.engine.annotation.MLAlgorithm;
import org.opensearch.ml.engine.exceptions.MetaDataException;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {
    private static final String ALGO_PACKAGE_NAME = "org.opensearch.ml.engine.algorithms";

    public static MLOutput predict(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame, Model model) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case KMEANS:
                KMeans kMeans = new KMeans((KMeansParams) parameters);
                return kMeans.predict(dataFrame, model);
            case LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression((LinearRegressionParams)parameters);
                return linearRegression.predict(dataFrame, model);
            case SAMPLE_ALGO:
                SampleAlgo sampleAlgo = new SampleAlgo((SampleAlgoParams) parameters);
                return sampleAlgo.predict(dataFrame, model);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }

    public static Model train(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case KMEANS:
                KMeans kMeans = new KMeans((KMeansParams) parameters);
                return kMeans.train(dataFrame);
            case LINEAR_REGRESSION:
                LinearRegression linearRegression = new LinearRegression((LinearRegressionParams) parameters);
                return linearRegression.train(dataFrame);
            case SAMPLE_ALGO:
                SampleAlgo sampleAlgo = new SampleAlgo((SampleAlgoParams) parameters);
                return sampleAlgo.train(dataFrame);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
    }

    public static MLOutput execute(MLAlgoName algoName, MLAlgoParams parameters, DataFrame dataFrame) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        switch (algoName) {
            case LOCAL_SAMPLE_CALCULATOR:
                LocalSampleCalculator localSampleAlgo = new LocalSampleCalculator();
                return localSampleAlgo.execute(parameters, dataFrame);
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
