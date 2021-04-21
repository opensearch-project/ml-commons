package org.opensearch.ml.engine.clustering;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValueBuilder;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.engine.Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class KMeansTest {
    private List<MLParameter> parameters = new ArrayList<>();
    private KMeans kMeans;
    private DataFrame trainDataFrame;
    private int trainSize = 100;

    @Before
    public void setUp() throws Exception {
        parameters.add(new MLParameter("seed", 1));
        parameters.add(new MLParameter("num_threads", 1));
        parameters.add(new MLParameter("distance_type", 0));
        parameters.add(new MLParameter("iterations", 10));
        parameters.add(new MLParameter("k", 2));

        kMeans = new KMeans(parameters);
        constructTrainDataFrame();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void predict() {
    }

    @Test
    public void train() throws IOException {
        Model model = kMeans.train(trainDataFrame);
        Assert.assertEquals("KMeans", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    private void constructTrainDataFrame() {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE)};
        trainDataFrame = new DefaultDataFrame(columnMetas);

        Random rng = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(new JDKRandomGenerator(rng.nextInt()),
                new double[]{0.0, 0.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(new JDKRandomGenerator(rng.nextInt()),
                new double[]{10.0, 10.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution[] gaussians = new MultivariateNormalDistribution[]{g1, g2};
        for (int i = 0; i < trainSize; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = gaussians[id].sample();
            Row row = new Row(2);
            row.setValue(0, ColumnValueBuilder.build(sample[0]));
            row.setValue(1, ColumnValueBuilder.build(sample[1]));
            trainDataFrame.appendRow(row);
        }
    }
}