/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.helper;

import lombok.experimental.UtilityClass;
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import java.util.Arrays;
import java.util.Random;

@UtilityClass
public class KMeansHelper {
    public static DataFrame constructKMeansDataFrame(int size) {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE)};
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{0.0, 0.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{10.0, 10.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[]{g1, g2};
        for (int i = 0; i < size; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            dataFrame.appendRow(Arrays.stream(sample).boxed().toArray(Double[]::new));
        }

        return dataFrame;
    }
}
