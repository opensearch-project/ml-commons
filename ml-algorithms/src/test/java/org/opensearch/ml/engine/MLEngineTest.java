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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;
import org.opensearch.ml.engine.contants.MLAlgoNames;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.opensearch.ml.engine.algorithms.clustering.KMeans.DISTANCE_TYPE;
import static org.opensearch.ml.engine.algorithms.clustering.KMeans.ITERATIONS;
import static org.opensearch.ml.engine.algorithms.clustering.KMeans.K;
import static org.opensearch.ml.engine.algorithms.clustering.KMeans.NUM_THREADS;
import static org.opensearch.ml.engine.algorithms.clustering.KMeans.SEED;
import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.BETA1;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.BETA2;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.EPSILON;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.LEARNING_RATE;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.OBJECTIVE;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.OPTIMISER;
import static org.opensearch.ml.engine.algorithms.regression.LinearRegression.TARGET;

public class MLEngineTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Set<String> algoNames = new HashSet<>();

    @Before
    public void setUp() {
        algoNames.add(MLAlgoNames.KMEANS);
        algoNames.add(MLAlgoNames.LINEAR_REGRESSION);
    }

    @Test
    public void predictKMeans() {
        Model model = trainKMeansModel();
        DataFrame predictionDataFrame = constructKMeansDataFrame(10);
        DataFrame predictions = MLEngine.predict("kmeans", null, predictionDataFrame, model);
        Assert.assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictLinearRegression() {
        Model model = trainLinearRegressionModel();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        DataFrame predictions = MLEngine.predict("linear_regression", null, predictionDataFrame, model);
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void trainKMeans() {
        Model model = trainKMeansModel();
        Assert.assertEquals("KMeans", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainLinearRegression() {
        Model model = trainLinearRegressionModel();
        Assert.assertEquals("LinearRegression", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainUnsupportedAlgorithm() {
        String algoName = "unsupported_algorithm";
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: " + algoName);
        MLEngine.train(algoName, null, null);
    }

    @Test
    public void predictUnsupportedAlgorithm() {
        String algoName = "unsupported_algorithm";
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported algorithm: " + algoName);
        MLEngine.predict(algoName, null, null, null);
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        MLEngine.predict("linear_regression", null, null, null);
    }

    @Test
    public void getMetaData() {
        MLEngineMetaData metaData = MLEngine.getMetaData();
        metaData.getAlgoMetaDataList().forEach(e -> Assert.assertTrue(algoNames.contains(e.getName())));
    }

    private Model trainKMeansModel() {
        List<MLParameter> parameters = new ArrayList<>();
        parameters.add(MLParameterBuilder.parameter(SEED, 1L));
        parameters.add(MLParameterBuilder.parameter(NUM_THREADS, 1));
        parameters.add(MLParameterBuilder.parameter(DISTANCE_TYPE, 0));
        parameters.add(MLParameterBuilder.parameter(ITERATIONS, 10));
        parameters.add(MLParameterBuilder.parameter(K, 2));
        DataFrame trainDataFrame = constructKMeansDataFrame(100);
        return MLEngine.train("kmeans", parameters, trainDataFrame);
    }

    private Model trainLinearRegressionModel() {
        List<MLParameter> parameters = new ArrayList<>();
        parameters.add(MLParameterBuilder.parameter(OBJECTIVE, 0));
        parameters.add(MLParameterBuilder.parameter(OPTIMISER, 5));
        parameters.add(MLParameterBuilder.parameter(LEARNING_RATE, 0.01));
        parameters.add(MLParameterBuilder.parameter(EPSILON, 1e-6));
        parameters.add(MLParameterBuilder.parameter(BETA1, 0.9));
        parameters.add(MLParameterBuilder.parameter(BETA2, 0.99));
        parameters.add(MLParameterBuilder.parameter(TARGET, "price"));
        DataFrame trainDataFrame = constructLinearRegressionTrainDataFrame();
        return MLEngine.train("linear_regression", parameters, trainDataFrame);
    }
}