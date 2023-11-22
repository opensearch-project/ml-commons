/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;

public class DataFrameBuilderTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void emptyDataFrame_Success() {
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.DOUBLE).build() };
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);
        assertEquals(0, dataFrame.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyDataFrame_Exception_NullColumnMetas() {
        DataFrameBuilder.emptyDataFrame(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyDataFrame_Exception_EmptyColumnMetas() {
        DataFrameBuilder.emptyDataFrame(new ColumnMeta[0]);
    }

    @Test
    public void load_Success_WithInputMapList() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", "string");
        map.put("k2", 1);
        map.put("k3", true);
        map.put("k4", 2.3D);
        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(map));
        assertEquals(1, dataFrame.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_EmptyInputMapList() {
        DataFrameBuilder.load(Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_NullInputMapList() {
        DataFrameBuilder.load((List<Map<String, Object>>) null);
    }

    @Test
    public void load_Success_ColumnMetasAndInputMapList() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", 2.3D);
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.DOUBLE).build() };
        DataFrame dataFrame = DataFrameBuilder.load(columnMetas, Collections.singletonList(map));
        assertEquals(1, dataFrame.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_EmptyColumnMetas() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", 2.3D);
        DataFrameBuilder.load(new ColumnMeta[0], Collections.singletonList(map));
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_NullColumnMetas() {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", 2.3D);
        DataFrameBuilder.load(null, Collections.singletonList(map));
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_ColumnMetasAndEmptyInputMapList() {
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.DOUBLE).build() };
        DataFrameBuilder.load(columnMetas, Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void load_Exception_ColumnMetasAndNullInputMapList() {
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.DOUBLE).build() };
        DataFrameBuilder.load(columnMetas, null);
    }

    @Test
    public void load_Exception_DifferentColumnsInColumnMetasAndInputMapList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input item map size is different in the map");

        Map<String, Object> map = new HashMap<>();
        map.put("k1", 2.3D);
        ColumnMeta[] columnMetas = new ColumnMeta[] {
            ColumnMeta.builder().name("k1").columnType(ColumnType.DOUBLE).build(),
            ColumnMeta.builder().name("k2").columnType(ColumnType.DOUBLE).build() };
        DataFrameBuilder.load(columnMetas, Collections.singletonList(map));
    }

    @Test
    public void load_Exception_DifferentTypesForSameField() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("the same field has different data type");

        Map<String, Object> map = new HashMap<>();
        map.put("k1", 2.3D);
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.INTEGER).build() };
        DataFrameBuilder.load(columnMetas, Collections.singletonList(map));
    }

    @Test
    public void load_Exception_DifferentFields() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("field of input item doesn't exist in columns, filed:k2");

        Map<String, Object> map = new HashMap<>();
        map.put("k2", 2.3D);
        ColumnMeta[] columnMetas = new ColumnMeta[] { ColumnMeta.builder().name("k1").columnType(ColumnType.INTEGER).build() };
        DataFrameBuilder.load(columnMetas, Collections.singletonList(map));
    }

    @Test
    public void load_Success_StreamInput() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("k1", "string");
        map.put("k2", 1);
        map.put("k3", true);
        map.put("k4", 2.3D);
        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(map));
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        dataFrame.writeTo(bytesStreamOutput);
        dataFrame = DataFrameBuilder.load(bytesStreamOutput.bytes().streamInput());
        assertEquals(1, dataFrame.size());
    }
}
