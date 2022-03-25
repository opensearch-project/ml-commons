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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@UtilityClass
public class MLTestHelper {

    public static final String TIME_FIELD = "timestamp";
    public static DataFrame constructTestDataFrame(int size) {
        return constructTestDataFrame(size, false);
    }

    public static DataFrame constructTestDataFrame(int size, boolean addTimeFiled) {
        List<ColumnMeta> columnMetaList =  new ArrayList<>();
        columnMetaList.add(new ColumnMeta("f1", ColumnType.DOUBLE));
        columnMetaList.add(new ColumnMeta("f2", ColumnType.DOUBLE));
        if (addTimeFiled) {
            columnMetaList.add(new ColumnMeta(TIME_FIELD, ColumnType.LONG));
        }
        ColumnMeta[] columnMetas = columnMetaList.toArray(new ColumnMeta[0]);
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{0.0, 0.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(new JDKRandomGenerator(random.nextInt()),
                new double[]{10.0, 10.0}, new double[][]{{2.0, 1.0}, {1.0, 2.0}});
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[]{g1, g2};
        long startTime = 1648154137000l;
        for (int i = 0; i < size; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            Object[] row = Arrays.stream(sample).boxed().toArray(Double[]::new);
            if (addTimeFiled) {
                long timestamp = startTime + 60_000 * i;
                row = new Object[] {row[0], row[1], timestamp};
            }
            dataFrame.appendRow(row);
        }

        return dataFrame;
    }
}
