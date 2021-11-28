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
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.LinearRegressionParams;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLPredictionOutput;

import java.util.HashSet;
import java.util.Set;

import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionPredictionDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;

public class MLEngineTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Set<String> algoNames = new HashSet<>();

    @Before
    public void setUp() {
        algoNames.add(FunctionName.KMEANS.getName());
        algoNames.add(FunctionName.LINEAR_REGRESSION.getName());
        algoNames.add(FunctionName.SAMPLE_ALGO.getName());
        MLCommonsClassLoader.loadClassMapping(MLCommonsClassLoader.class, "/ml-commons-config.yml");
        MLCommonsClassLoader.loadClassMapping(MLEngine.class, "/ml-algorithm-config.yml");
    }

    @Test
    public void predictKMeans() {
        Model model = trainKMeansModel();
        DataFrame predictionDataFrame = constructKMeansDataFrame(10);
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(FunctionName.KMEANS, null, predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void predictLinearRegression() {
        Model model = trainLinearRegressionModel();
        DataFrame predictionDataFrame = constructLinearRegressionPredictionDataFrame();
        MLPredictionOutput output = (MLPredictionOutput)MLEngine.predict(FunctionName.LINEAR_REGRESSION, null, predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(2, predictions.size());
    }

    @Test
    public void trainKMeans() {
        Model model = trainKMeansModel();
        Assert.assertEquals(FunctionName.KMEANS.getName(), model.getName());
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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Algo name should not be null");
        MLEngine.train(null, null, null);
    }

    @Test
    public void predictUnsupportedAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Algo name should not be null");
        MLEngine.predict(null, null, null, null);
    }

    @Test
    public void predictWithoutModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for linear regression prediction.");
        MLEngine.predict(FunctionName.LINEAR_REGRESSION, null, null, null);
    }

    @Test
    public void getMetaData() {
        MLEngineMetaData metaData = MLEngine.getMetaData();
        metaData.getAlgoMetaDataList().forEach(e -> Assert.assertTrue(algoNames.contains(e.getName())));
    }

    private Model trainKMeansModel() {
        KMeansParams parameters = KMeansParams.builder()
                .centroids(2)
                .iterations(10)
                .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
                .build();
        DataFrame trainDataFrame = constructKMeansDataFrame(100);
        return MLEngine.train(FunctionName.KMEANS, parameters, trainDataFrame);
    }

    private Model trainLinearRegressionModel() {
        LinearRegressionParams parameters = LinearRegressionParams.builder()
                .objectiveType(LinearRegressionParams.ObjectiveType.SQUARED_LOSS)
                .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .learningRate(0.01)
                .epsilon(1e-6)
                .beta1(0.9)
                .beta2(0.99)
                .target("price")
                .build();
        DataFrame trainDataFrame = constructLinearRegressionTrainDataFrame();


        return MLEngine.train(FunctionName.LINEAR_REGRESSION, parameters, trainDataFrame);
    }
}