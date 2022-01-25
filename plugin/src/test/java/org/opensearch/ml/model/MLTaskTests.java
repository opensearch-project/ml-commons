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

package org.opensearch.ml.model;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.utils.TestHelper;

public class MLTaskTests {
    private MLTask mlTask;

    @Before
    public void setUp() throws ParseException {
        Instant time = Instant.ofEpochSecond(1641600000);
        mlTask = MLTask
            .builder()
            .taskId("dummy taskId")
            .modelId("test_model_id")
            .taskType(MLTaskType.PREDICTION)
            .functionName(FunctionName.KMEANS)
            .state(MLTaskState.RUNNING)
            .inputType(MLInputDataType.DATA_FRAME)
            .workerNode("node1")
            .progress(0.0f)
            .outputIndex("test_index")
            .error("test_error")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .lastUpdateTime(time)
            .build();
    }

    @Test
    public void testWriteTo() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlTask.writeTo(output);
        MLTask task2 = new MLTask(output.bytes().streamInput());
        assertEquals(mlTask, task2);
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlTask.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"task_id\":\"dummy taskId\",\"model_id\":\"test_model_id\",\"task_type\":\"PREDICTION\","
                + "\"function_name\":\"KMEANS\",\"state\":\"RUNNING\",\"input_type\":\"DATA_FRAME\",\"progress\":0.0,"
                + "\"output_index\":\"test_index\",\"worker_node\":\"node1\",\"create_time\":1641599940000,"
                + "\"last_update_time\":1641600000000,\"error\":\"test_error\",\"is_async\":false}",
            taskContent
        );
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        MLTask task = MLTask.builder().build();
        task.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"is_async\":false}", taskContent);
    }
}
