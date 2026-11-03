/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.IntValue;
import org.opensearch.ml.common.dataframe.Row;

public class MLTaskOutputTest {

    MLTaskOutput output;

    @Before
    public void setUp() {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("test", ColumnType.INTEGER) };
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(new ColumnValue[] { new IntValue(1) }));
        rows.add(new Row(new ColumnValue[] { new IntValue(2) }));
        Map<String, Object> response = new HashMap<>();
        response.put("memory_id", "test-memory-id");
        output = MLTaskOutput.builder().taskId("test_task_id").status("test_status").response(response).build();
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        XContentBuilder builderWithExecuteResponse = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{\"task_id\":\"test_task_id\",\"status\":\"test_status\",\"response\":{\"memory_id\":\"test-memory-id\"}}", jsonStr);
        output.toXContent(builderWithExecuteResponse, ToXContent.EMPTY_PARAMS);
        String jsonStr2 = builderWithExecuteResponse.toString();
        assertEquals("{\"task_id\":\"test_task_id\",\"status\":\"test_status\",\"response\":{\"memory_id\":\"test-memory-id\"}}", jsonStr2);
    }

    @Test
    public void toXContent_EmptyOutput() throws IOException {
        MLTaskOutput output = MLTaskOutput.builder().build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{}", jsonStr);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(output);
    }

    private void readInputStream(MLTaskOutput output) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        output.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLOutputType outputType = streamInput.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.ML_TASK_OUTPUT, outputType);
        MLTaskOutput parsedOutput = new MLTaskOutput(streamInput);
        assertEquals(output.getType(), parsedOutput.getType());
        assertEquals(output.getTaskId(), parsedOutput.getTaskId());
        assertEquals(output.getStatus(), parsedOutput.getStatus());
    }
}
