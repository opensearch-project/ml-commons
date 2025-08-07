package org.opensearch.ml.common.transport.task;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;

public class MLTaskGetResponseTest {
    MLTask mlTask;

    @Before
    public void setUp() {
        mlTask = MLTask
            .builder()
            .taskId("id")
            .modelId("model id")
            .taskType(MLTaskType.EXECUTION)
            .functionName(FunctionName.LINEAR_REGRESSION)
            .state(MLTaskState.CREATED)
            .inputType(MLInputDataType.DATA_FRAME)
            .progress(1.3f)
            .outputIndex("some index")
            .workerNodes(Arrays.asList("some node"))
            .createTime(Instant.ofEpochMilli(123))
            .lastUpdateTime(Instant.ofEpochMilli(123))
            .error("error")
            .user(new User())
            .async(true)
            .build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLTaskGetResponse response = MLTaskGetResponse.builder().mlTask(mlTask).build();
        response.writeTo(bytesStreamOutput);
        MLTaskGetResponse parsedResponse = new MLTaskGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.mlTask, parsedResponse.mlTask);
        assertEquals(response.mlTask.getTaskId(), parsedResponse.mlTask.getTaskId());
        assertEquals(response.mlTask.getModelId(), parsedResponse.mlTask.getModelId());
        assertEquals(response.mlTask.getTaskType(), parsedResponse.mlTask.getTaskType());
        assertEquals(response.mlTask.getFunctionName(), parsedResponse.mlTask.getFunctionName());
        assertEquals(response.mlTask.getState(), parsedResponse.mlTask.getState());
        assertEquals(response.mlTask.getInputType(), parsedResponse.mlTask.getInputType());
        assertEquals(response.mlTask.getProgress(), parsedResponse.mlTask.getProgress());
        assertEquals(response.mlTask.getOutputIndex(), parsedResponse.mlTask.getOutputIndex());
        assertEquals(response.mlTask.getWorkerNodes(), parsedResponse.mlTask.getWorkerNodes());
        assertEquals(response.mlTask.getCreateTime(), parsedResponse.mlTask.getCreateTime());
        assertEquals(response.mlTask.getLastUpdateTime(), parsedResponse.mlTask.getLastUpdateTime());
        assertEquals(response.mlTask.getError(), parsedResponse.mlTask.getError());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLTaskGetResponse mlTaskGetResponse = MLTaskGetResponse.builder().mlTask(mlTask).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlTaskGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(
            "{\"task_id\":\"id\","
                + "\"model_id\":\"model id\","
                + "\"task_type\":\"EXECUTION\","
                + "\"function_name\":\"LINEAR_REGRESSION\","
                + "\"state\":\"CREATED\","
                + "\"input_type\":\"DATA_FRAME\","
                + "\"progress\":1.3,"
                + "\"output_index\":\"some index\","
                + "\"worker_node\":[\"some node\"],"
                + "\"create_time\":123,"
                + "\"last_update_time\":123,"
                + "\"error\":\"error\","
                + "\"user\":{\"name\":\"\",\"backend_roles\":[],\"roles\":[],\"custom_attribute_names\":[],\"user_requested_tenant\":null},"
                + "\"is_async\":true}",
            jsonStr
        );
    }
}
