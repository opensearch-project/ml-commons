package org.opensearch.ml.common.transport.deploy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTaskType;

public class MLDeployModelResponseTest {

    private String taskId;
    private String status;
    private MLTaskType taskType;

    @Before
    public void setUp() throws Exception {
        taskId = "test_id";
        status = "test";
        taskType = MLTaskType.DEPLOY_MODEL;
    }

    @Test
    public void writeTo_Success() throws IOException {
        // Setup
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLDeployModelResponse response = new MLDeployModelResponse(taskId, taskType, status);
        // Run the test
        response.writeTo(bytesStreamOutput);
        MLDeployModelResponse parsedResponse = new MLDeployModelResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getTaskId(), parsedResponse.getTaskId());
        assertEquals(response.getTaskType(), parsedResponse.getTaskType());
        assertEquals(response.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLDeployModelResponse response = new MLDeployModelResponse(taskId, taskType, status);
        // Run the test
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        // Verify the results
        assertEquals("{\"task_id\":\"test_id\"," + "\"task_type\":\"DEPLOY_MODEL\"," + "\"status\":\"test\"}", jsonStr);
    }
}
