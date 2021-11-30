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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;

import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;


public class KMeansTest {
    private KMeansParams parameters;
    private KMeans kMeans;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;
    private int trainSize = 100;
    private int predictionSize = 10;

    @Before
    public void setUp() {
        parameters = KMeansParams.builder()
                .distanceType(KMeansParams.DistanceType.EUCLIDEAN)
                .iterations(10)
                .centroids(2)
                .build();

        kMeans = new KMeans(parameters);
        constructKMeansTrainDataFrame();
        constructKMeansPredictionDataFrame();
    }

    @Test
    public void predict() {
        Model model = kMeans.train(trainDataFrame);
        MLPredictionOutput output = (MLPredictionOutput) kMeans.predict(predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        Assert.assertEquals(predictionSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void train() {
        Model model = kMeans.train(trainDataFrame);
        Assert.assertEquals(FunctionName.KMEANS.name(), model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    private void constructKMeansPredictionDataFrame() {
        predictionDataFrame = constructKMeansDataFrame(predictionSize);
    }

    private void constructKMeansTrainDataFrame() {
        trainDataFrame = constructKMeansDataFrame(trainSize);
    }

}