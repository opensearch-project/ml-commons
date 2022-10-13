/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.params;

import static org.opensearch.ml.utils.TestHelper.parser;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.utils.TestData;
import org.opensearch.test.OpenSearchTestCase;

public class MLInputTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testParseKmeansInputQuery() throws IOException {
        String query =
            "{\"input_query\":{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"k1\":1}}]}},\"size\":10},\"input_index\":[\"test_data\"]}";
        XContentParser parser = parser(query);
        MLInput mlInput = MLInput.parse(parser, FunctionName.KMEANS.name());
        String expectedQuery =
            "{\"size\":10,\"query\":{\"bool\":{\"filter\":[{\"term\":{\"k1\":{\"value\":1,\"boost\":1.0}}}],\"adjust_pure_negative\":true,\"boost\":1.0}}}";
        SearchQueryInputDataset inputDataset = (SearchQueryInputDataset) mlInput.getInputDataset();
        assertEquals(expectedQuery, inputDataset.getSearchSourceBuilder().toString());
    }

    @Ignore
    public void testParseKmeansInputDataFrame() throws IOException {
        String query = "{\"input_data\":{\"column_metas\":[{\"name\":\"total_sum\",\"column_type\":\"DOUBLE\"},{\"name\":\"is_error\","
            + "\"column_type\":\"BOOLEAN\"}],\"rows\":[{\"values\":[{\"column_type\":\"DOUBLE\",\"value\":15},"
            + "{\"column_type\":\"BOOLEAN\",\"value\":false}]},{\"values\":[{\"column_type\":\"DOUBLE\",\"value\":100},"
            + "{\"column_type\":\"BOOLEAN\",\"value\":true}]}]}}";
        XContentParser parser = parser(query);
        MLInput mlInput = MLInput.parse(parser, FunctionName.KMEANS.name());
        DataFrameInputDataset inputDataset = (DataFrameInputDataset) mlInput.getInputDataset();
        DataFrame dataFrame = inputDataset.getDataFrame();

        assertEquals(2, dataFrame.columnMetas().length);
        assertEquals(ColumnType.DOUBLE, dataFrame.columnMetas()[0].getColumnType());
        assertEquals(ColumnType.BOOLEAN, dataFrame.columnMetas()[1].getColumnType());
        assertEquals("total_sum", dataFrame.columnMetas()[0].getName());
        assertEquals("is_error", dataFrame.columnMetas()[1].getName());

        assertEquals(ColumnType.DOUBLE, dataFrame.getRow(0).getValue(0).columnType());
        assertEquals(ColumnType.BOOLEAN, dataFrame.getRow(0).getValue(1).columnType());
        assertEquals(15.0, dataFrame.getRow(0).getValue(0).getValue());
        assertEquals(false, dataFrame.getRow(0).getValue(1).getValue());
    }

    public void testWithoutAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInput.builder().inputDataset(new DataFrameInputDataset(TestData.constructTestDataFrame(10))).build();
    }

}
