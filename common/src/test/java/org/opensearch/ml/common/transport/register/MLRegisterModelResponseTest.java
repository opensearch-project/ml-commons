package org.opensearch.ml.common.transport.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLRegisterModelResponseTest {

    private String taskId;
    private String status;
    private String modelId;

    @Before
    public void setUp() throws Exception {
        taskId = "test_id";
        status = "test";
        modelId = "model_id";
    }

    @Test
    public void writeTo_Success() throws IOException {
        // Setup
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLRegisterModelResponse response = new MLRegisterModelResponse(taskId, status, modelId);
        // Run the test
        response.writeTo(bytesStreamOutput);
        MLRegisterModelResponse parsedResponse = new MLRegisterModelResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getTaskId(), parsedResponse.getTaskId());
        assertEquals(response.getStatus(), parsedResponse.getStatus());
        assertEquals(response.getModelId(), parsedResponse.getModelId());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLRegisterModelResponse response = new MLRegisterModelResponse(taskId, status);
        // Run the test
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        // Verify the results
        assertEquals("{\"task_id\":\"test_id\"," + "\"status\":\"test\"}", jsonStr);
    }

    @Test
    public void testToXContent_withModelId() throws IOException {
        // Setup
        MLRegisterModelResponse response = new MLRegisterModelResponse(taskId, status, modelId);
        // Run the test
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        // Verify the results
        assertEquals("{\"task_id\":\"test_id\"," + "\"status\":\"test\"," + "\"model_id\":\"model_id\"}", jsonStr);
    }
}
