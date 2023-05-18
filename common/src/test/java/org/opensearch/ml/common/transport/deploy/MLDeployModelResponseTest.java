package org.opensearch.ml.common.transport.deploy;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MLDeployModelResponseTest {

    private String taskId;
    private String status;

    @Before
    public void setUp() throws Exception {
        taskId = "test_id";
        status = "test";
    }

    @Test
    public void writeTo_Success() throws IOException {
        // Setup
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLDeployModelResponse response = new MLDeployModelResponse(taskId, status);
        // Run the test
        response.writeTo(bytesStreamOutput);
        MLDeployModelResponse parsedResponse = new MLDeployModelResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getTaskId(), parsedResponse.getTaskId());
        assertEquals(response.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLDeployModelResponse response = new MLDeployModelResponse(taskId, status);
        // Run the test
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = org.opensearch.common.Strings.toString(builder);
        // Verify the results
        assertEquals("{\"task_id\":\"test_id\"," +
                "\"status\":\"test\"}", jsonStr);
    }
}
