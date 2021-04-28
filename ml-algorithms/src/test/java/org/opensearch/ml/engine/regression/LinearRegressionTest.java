package org.opensearch.ml.engine.regression;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;
import org.opensearch.ml.engine.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LinearRegressionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private List<MLParameter> parameters = new ArrayList<>();
    private DataFrame trainDataFrame;

    @Before
    public void setUp() {
        parameters.add(MLParameterBuilder.parameter("objective", 0));
        parameters.add(MLParameterBuilder.parameter("optimiser", 5));
        parameters.add(MLParameterBuilder.parameter("learning_rate", 0.01));
        parameters.add(MLParameterBuilder.parameter("epsilon", 1e-6));
        parameters.add(MLParameterBuilder.parameter("beta1", 0.9));
        parameters.add(MLParameterBuilder.parameter("beta2", 0.99));
        constructLinearRegressionTrainDataFrame();
    }

    @Test
    public void train() {
        parameters.add(MLParameterBuilder.parameter("target", "price"));
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
        Assert.assertEquals("LinearRegression", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainExceptionWithoutTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Unknown target when generating dataset from data frame for regression.");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
    }

    private void constructLinearRegressionTrainDataFrame() {
        double[] feet = new double[]{1000.00, 1500.00, 2000.00, 2500.00, 3000.00, 3500.00, 4000.00, 4500.00};
        double[] prices = new double[]{10000.00, 15000.00, 20000.00, 25000.00, 30000.00, 35000.00, 40000.00, 45000.00};
        String[] columnNames = new String[]{"feet", "price"};
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> new ColumnMeta(e, ColumnType.DOUBLE)).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i=0; i<prices.length; ++i) {
            Map<String, Object> row = new HashMap<>();
            row.put("feet", feet[i]);
            row.put("price", prices[i]);
            rows.add(row);
        }
        trainDataFrame = DataFrameBuilder.load(columnMetas, rows);
    }
}