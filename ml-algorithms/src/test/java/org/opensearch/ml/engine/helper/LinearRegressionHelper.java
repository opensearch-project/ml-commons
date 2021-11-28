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

package org.opensearch.ml.engine.helper;

import lombok.experimental.UtilityClass;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class LinearRegressionHelper {
    public static DataFrame constructLinearRegressionTrainDataFrame() {
        double[] feet = new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        double[] prices = new double[]{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0};
        String[] columnNames = new String[]{"feet", "price"};
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> new ColumnMeta(e, ColumnType.DOUBLE)).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i=0; i<prices.length; ++i) {
            Map<String, Object> row = new HashMap<>();
            row.put("feet", feet[i]);
            row.put("price", prices[i]);
            rows.add(row);
        }

        return DataFrameBuilder.load(columnMetas, rows);
    }

    public static DataFrame constructLinearRegressionPredictionDataFrame() {
        double[] feet = new double[]{10, 20};
        String[] columnNames = new String[]{"feet"};
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> new ColumnMeta(e, ColumnType.DOUBLE)).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i=0; i<feet.length; ++i) {
            Map<String, Object> row = new HashMap<>();
            row.put("feet", feet[i]);
            rows.add(row);
        }
        return DataFrameBuilder.load(columnMetas, rows);
    }
}
