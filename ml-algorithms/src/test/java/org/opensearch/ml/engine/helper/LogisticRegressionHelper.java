/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LogisticRegressionHelper {
    public static DataFrame constructLogisticRegressionTrainDataFrame() {
        double[] heights = new double[] { 175.0, 172.0, 180.0, 165.0, 160.0, 163.0, 182.0, 190.0, 170.0 };
        String[] classes = new String[] { "medium", "medium", "tall", "short", "short", "short", "tall", "tall", "medium" };
        String[] columnNames = new String[] { "height", "class" };
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> {
            if (e.equals("class")) {
                return new ColumnMeta(e, ColumnType.STRING);
            }
            return new ColumnMeta(e, ColumnType.DOUBLE);
        }).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < heights.length; ++i) {
            Map<String, Object> row = new HashMap<>();
            row.put("height", heights[i]);
            row.put("class", classes[i]);
            rows.add(row);
        }

        return DataFrameBuilder.load(columnMetas, rows);
    }

    public static DataFrame constructLogisticRegressionPredictionDataFrame() {
        double[] heights = new double[] { 181, 171 };
        String[] columnNames = new String[] { "height" };
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> new ColumnMeta(e, ColumnType.DOUBLE)).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < heights.length; ++i) {
            Map<String, Object> row = new HashMap<>();
            row.put("height", heights[i]);
            rows.add(row);
        }
        return DataFrameBuilder.load(columnMetas, rows);
    }
}
