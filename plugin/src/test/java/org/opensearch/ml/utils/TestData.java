/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class TestData {

    public static DataFrame constructTestDataFrame(int size) {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE) };
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 0.0, 0.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 10.0, 10.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[] { g1, g2 };
        for (int i = 0; i < size; ++i) {
            int id = 0;
            if (random.nextDouble() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            dataFrame.appendRow(Arrays.stream(sample).boxed().toArray(Double[]::new));
        }

        return dataFrame;
    }

    public static final int IRIS_DATA_SIZE = 150;
    public static final String IRIS_DATA = "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.7,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.6,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.6,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.9,\"petal_length_in_cm\":1.7,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.6,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.4,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.7,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.8,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.8,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.3,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.1,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":4.0,\"petal_length_in_cm\":1.2,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":4.4,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.9,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":1.7,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.7,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.7,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.6,\"sepal_width_in_cm\":3.6,\"petal_length_in_cm\":1.0,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":1.7,\"petal_width_in_cm\":0.5,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.8,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.9,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.2,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.2,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.7,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.8,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.2,\"sepal_width_in_cm\":4.1,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":4.2,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":1.2,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.1,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.4,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.5,\"sepal_width_in_cm\":2.3,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.4,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":1.3,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.5,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.6,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":1.9,\"petal_width_in_cm\":0.4,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.8,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.3,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":1.6,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.6,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.3,\"sepal_width_in_cm\":3.7,\"petal_length_in_cm\":1.5,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"class\":\"Iris-setosa\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.0,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":4.7,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":4.9,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":2.3,\"petal_length_in_cm\":4.0,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.5,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.6,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":4.7,\"petal_width_in_cm\":1.6,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":2.4,\"petal_length_in_cm\":3.3,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.6,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.6,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.2,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":3.9,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":2.0,\"petal_length_in_cm\":3.5,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.9,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.2,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":2.2,\"petal_length_in_cm\":4.0,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.7,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":3.6,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":4.4,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":4.1,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.2,\"sepal_width_in_cm\":2.2,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":3.9,\"petal_width_in_cm\":1.1,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.9,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":4.8,\"petal_width_in_cm\":1.8,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.0,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":4.9,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.7,\"petal_width_in_cm\":1.2,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.3,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.6,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.4,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.8,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.8,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.0,\"petal_width_in_cm\":1.7,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":2.6,\"petal_length_in_cm\":3.5,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":2.4,\"petal_length_in_cm\":3.8,\"petal_width_in_cm\":1.1,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":2.4,\"petal_length_in_cm\":3.7,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":3.9,\"petal_width_in_cm\":1.2,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":1.6,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.4,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.6,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":4.7,\"petal_width_in_cm\":1.5,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.3,\"petal_length_in_cm\":4.4,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.1,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":4.0,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.5,\"sepal_width_in_cm\":2.6,\"petal_length_in_cm\":4.4,\"petal_width_in_cm\":1.2,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.6,\"petal_width_in_cm\":1.4,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.6,\"petal_length_in_cm\":4.0,\"petal_width_in_cm\":1.2,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.0,\"sepal_width_in_cm\":2.3,\"petal_length_in_cm\":3.3,\"petal_width_in_cm\":1.0,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":4.2,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.2,\"petal_width_in_cm\":1.2,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.2,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.2,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":4.3,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":3.0,\"petal_width_in_cm\":1.1,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.1,\"petal_width_in_cm\":1.3,\"class\":\"Iris-versicolor\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":6.0,\"petal_width_in_cm\":2.5,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":1.9,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.1,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.9,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.5,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.8,\"petal_width_in_cm\":2.2,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.6,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":6.6,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":4.9,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.7,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.3,\"sepal_width_in_cm\":2.9,\"petal_length_in_cm\":6.3,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":5.8,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.2,\"sepal_width_in_cm\":3.6,\"petal_length_in_cm\":6.1,\"petal_width_in_cm\":2.5,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.5,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":5.3,\"petal_width_in_cm\":1.9,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.8,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.5,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.7,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":5.0,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":2.4,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":5.3,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.5,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.5,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.7,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":6.7,\"petal_width_in_cm\":2.2,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.7,\"sepal_width_in_cm\":2.6,\"petal_length_in_cm\":6.9,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":2.2,\"petal_length_in_cm\":5.0,\"petal_width_in_cm\":1.5,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.9,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":5.7,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.6,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.9,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.7,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":6.7,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":4.9,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":5.7,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.2,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":6.0,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.2,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":4.8,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.9,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.2,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.8,\"petal_width_in_cm\":1.6,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.4,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":6.1,\"petal_width_in_cm\":1.9,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.9,\"sepal_width_in_cm\":3.8,\"petal_length_in_cm\":6.4,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":2.2,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.8,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":1.5,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.1,\"sepal_width_in_cm\":2.6,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":1.4,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":7.7,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":6.1,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":2.4,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":5.5,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.0,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":4.8,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":5.4,\"petal_width_in_cm\":2.1,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":5.6,\"petal_width_in_cm\":2.4,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.9,\"sepal_width_in_cm\":3.1,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.8,\"sepal_width_in_cm\":2.7,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":1.9,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.8,\"sepal_width_in_cm\":3.2,\"petal_length_in_cm\":5.9,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.3,\"petal_length_in_cm\":5.7,\"petal_width_in_cm\":2.5,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.7,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.2,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.3,\"sepal_width_in_cm\":2.5,\"petal_length_in_cm\":5.0,\"petal_width_in_cm\":1.9,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.5,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.2,\"petal_width_in_cm\":2.0,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":6.2,\"sepal_width_in_cm\":3.4,\"petal_length_in_cm\":5.4,\"petal_width_in_cm\":2.3,\"class\":\"Iris-virginica\"}\n"
        + "{ \"index\" : { \"_index\" : \"iris_data\" } }\n"
        + "{\"sepal_length_in_cm\":5.9,\"sepal_width_in_cm\":3.0,\"petal_length_in_cm\":5.1,\"petal_width_in_cm\":1.8,\"class\":\"Iris-virginica\"}\n";

    public static final String trainModelDataJson() {
        JsonObject column_metas_1 = new JsonObject();
        JsonObject column_metas_2 = new JsonObject();
        JsonArray column_metas = new JsonArray();
        column_metas_1.addProperty("name", "total_sum");
        column_metas_1.addProperty("column_type", "DOUBLE");

        column_metas_2.addProperty("name", "is_error");
        column_metas_2.addProperty("column_type", "BOOLEAN");

        column_metas.add(column_metas_1);
        column_metas.add(column_metas_2);

        JsonObject rows_values_1 = new JsonObject();
        JsonObject rows_values_2 = new JsonObject();

        rows_values_1.addProperty("column_type", "DOUBLE");
        rows_values_1.addProperty("value", 15);

        rows_values_2.addProperty("column_type", "BOOLEAN");
        rows_values_2.addProperty("value", false);

        JsonArray rows_values = new JsonArray();
        rows_values.add(rows_values_1);
        rows_values.add(rows_values_2);

        JsonArray rows = new JsonArray();
        JsonObject value = new JsonObject();
        value.add("values", rows_values);
        rows.add(value);

        JsonObject input_data = new JsonObject();
        input_data.add("column_metas", column_metas);
        input_data.add("rows", rows);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("sample_param", 10);

        JsonObject body = new JsonObject();
        body.add("parameters", parameters);
        body.add("input_data", input_data);

        return body.toString();
    }

    public static final String matchAllSearchQuery() {
        String matchAllQuery = "{\"query\": {" + "\"match_all\": {}" + "}" + "}";
        return matchAllQuery;
    }
}
