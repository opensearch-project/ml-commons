package org.opensearch.ml.engine;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;
import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;

public class MLEngineTest {

    @Test
    public void predictKMeans() {
        Model model = trainKMeansModel();
        DataFrame predictionDataFrame = constructKMeansDataFrame(10);
        DataFrame predictions = MLEngine.predict("kmeans", null, predictionDataFrame, model);
        Assert.assertEquals(10, predictions.size());
        predictions.forEach(row -> Assert.assertTrue(row.getValue(0).intValue() == 0 || row.getValue(0).intValue() == 1));
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

    private Model trainKMeansModel() {
        List<MLParameter> parameters = new ArrayList<>();
        parameters.add(MLParameterBuilder.parameter("seed", 1L));
        parameters.add(MLParameterBuilder.parameter("num_threads", 1));
        parameters.add(MLParameterBuilder.parameter("distance_type", 0));
        parameters.add(MLParameterBuilder.parameter("iterations", 10));
        parameters.add(MLParameterBuilder.parameter("k", 2));
        DataFrame trainDataFrame = constructKMeansDataFrame(100);
        return MLEngine.train("kmeans", parameters, trainDataFrame);
    }

    private Model trainLinearRegressionModel() {
        List<MLParameter> parameters = new ArrayList<>();
        parameters.add(MLParameterBuilder.parameter("objective", 0));
        parameters.add(MLParameterBuilder.parameter("optimiser", 5));
        parameters.add(MLParameterBuilder.parameter("learning_rate", 0.01));
        parameters.add(MLParameterBuilder.parameter("epsilon", 1e-6));
        parameters.add(MLParameterBuilder.parameter("beta1", 0.9));
        parameters.add(MLParameterBuilder.parameter("beta2", 0.99));
        parameters.add(MLParameterBuilder.parameter("target", "price"));
        DataFrame trainDataFrame = constructLinearRegressionTrainDataFrame();
        return MLEngine.train("linear_regression", parameters, trainDataFrame);
    }
}