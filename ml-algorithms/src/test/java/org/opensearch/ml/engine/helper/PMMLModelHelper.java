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

import java.util.*;

@UtilityClass
public class PMMLModelHelper {
    public static DataFrame constructPMMLModelDataFrame() {
        double[] values = new double[]{1, 2, 10, 100};
        String[] columnNames = new String[]{"value"};
        ColumnMeta[] columnMetas = Arrays.stream(columnNames).map(e -> new ColumnMeta(e, ColumnType.DOUBLE)).toArray(ColumnMeta[]::new);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("value", values[i]);
            rows.add(row);
        }
        return DataFrameBuilder.load(columnMetas, rows);
    }

    public static DataFrame constructExpectedPredictions() {
        Double[] values = new Double[]{0.010751943968992317, 0.09082283791718504, -0.120896777470202, -0.120896777470202};
        Boolean[] booleans = new Boolean[]{false, false, true, true};
        List<Map<String, Object>> predictions = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            Map<String, Object> prediction = new HashMap<>();
            prediction.put("outlier", booleans[i]);
            prediction.put("decisionFunction", values[i]);
            predictions.add(prediction);
        }
        ColumnMeta[] columnMetas = new ColumnMeta[2];
        columnMetas[0] = new ColumnMeta("outlier", ColumnType.BOOLEAN);
        columnMetas[1] = new ColumnMeta("decisionFunction", ColumnType.DOUBLE);
        return DataFrameBuilder.load(columnMetas, predictions);
    }
}
