package org.opensearch.ml.engine.clustering;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;
import org.opensearch.ml.engine.Model;

import java.util.ArrayList;
import java.util.List;

import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;


public class KMeansTest {
    private List<MLParameter> parameters = new ArrayList<>();
    private KMeans kMeans;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;
    private int trainSize = 100;
    private int predictionSize = 10;

    @Before
    public void setUp() {
        parameters.add(MLParameterBuilder.parameter("seed", 1L));
        parameters.add(MLParameterBuilder.parameter("num_threads", 1));
        parameters.add(MLParameterBuilder.parameter("distance_type", 0));
        parameters.add(MLParameterBuilder.parameter("iterations", 10));
        parameters.add(MLParameterBuilder.parameter("k", 2));

        kMeans = new KMeans(parameters);
        constructKMeansTrainDataFrame();
        constructKMeansPredictionDataFrame();
    }

    @Test
    public void predict() {
        Model model = kMeans.train(trainDataFrame);
        DataFrame predictions = kMeans.predict(predictionDataFrame, model);
        Assert.assertEquals(predictionSize, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
    }

    @Test
    public void train() {
        Model model = kMeans.train(trainDataFrame);
        Assert.assertEquals("KMeans", model.getName());
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