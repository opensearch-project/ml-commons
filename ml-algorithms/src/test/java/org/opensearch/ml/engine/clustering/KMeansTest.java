package org.opensearch.ml.engine.clustering;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;
import org.opensearch.ml.engine.Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class KMeansTest {
    private List<MLParameter> parameters = new ArrayList<>();
    private KMeans kMeans;
    private DataFrame trainDataFrame;
    private int trainSize = 100;

    @Before
    public void setUp() {
        parameters.add(MLParameterBuilder.parameter("seed", 1));
        parameters.add(MLParameterBuilder.parameter("num_threads", 1));
        parameters.add(MLParameterBuilder.parameter("distance_type", 0));
        parameters.add(MLParameterBuilder.parameter("iterations", 10));
        parameters.add(MLParameterBuilder.parameter("k", 2));

        kMeans = new KMeans(parameters);
        constructKMeansTrainDataFrame();
    }

    @Test
    public void train() throws IOException {
        Model model = kMeans.train(trainDataFrame);
        Assert.assertEquals("KMeans", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    private void constructKMeansTrainDataFrame() {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE)};
        trainDataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{0.0, 0.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{10.0, 10.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[]{g1, g2};
        for (int i = 0; i < trainSize; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            trainDataFrame.appendRow(Arrays.stream(sample).boxed().toArray(Double[]::new));
        }
    }
}