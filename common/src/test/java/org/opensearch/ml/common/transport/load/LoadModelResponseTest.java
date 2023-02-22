package org.opensearch.ml.common.transport.load;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LoadModelResponseTest {

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
        LoadModelResponse response = new LoadModelResponse(taskId, status);
        // Run the test
        response.writeTo(bytesStreamOutput);
        LoadModelResponse parsedResponse = new LoadModelResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getTaskId(), parsedResponse.getTaskId());
        assertEquals(response.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        LoadModelResponse response = new LoadModelResponse(taskId, status);
        // Run the test
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        // Verify the results
        assertEquals("{\"task_id\":\"test_id\"," +
                "\"status\":\"test\"}", jsonStr);
    }
}
