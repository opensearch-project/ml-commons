package org.opensearch.ml.common.transport.forward;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.output.MLPredictionOutput;

@RunWith(MockitoJUnitRunner.class)
public class MLForwardResponseTest {

    private MLPredictionOutput predictionOutput;
    private String status;

    @Before
    public void setUp() throws Exception {
        status = "test";
        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        predictionOutput = MLPredictionOutput.builder().status("Success").predictionResult(dataFrame).taskId("taskId").build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        // Setup
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLForwardResponse response = new MLForwardResponse(status, predictionOutput);
        // Run the test
        response.writeTo(bytesStreamOutput);
        MLForwardResponse parsedResponse = new MLForwardResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getStatus(), parsedResponse.getStatus());
        assertEquals(response.getMlOutput().toString(), parsedResponse.getMlOutput().toString());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLForwardResponse response = new MLForwardResponse(status, predictionOutput);
        // Run the test
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        // Verify the results
        assertEquals(
            "{\"result\":{\"task_id\":\"taskId\",\"status\":\"Success\",\"prediction_result\":{\"column_metas\":[{\"name\":\"key1\",\"column_type\":\"DOUBLE\"}],\"rows\":[{\"values\":[{\"column_type\":\"DOUBLE\",\"value\":2.0}]}]}}}",
            jsonStr
        );
    }

    @Test
    public void fromActionResponse_Success() {
        MLForwardResponse response = new MLForwardResponse(status, predictionOutput);

        assertSame(MLForwardResponse.fromActionResponse(response), response);
    }

    @Test
    // Maybe there's a better way to test the branch in the writeTo method(L50-54)
    public void writeTo_WithNullMLOutput() {
        MLForwardResponse response = new MLForwardResponse(status, null);
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        assertNull(MLForwardResponse.fromActionResponse(actionResponse).getMlOutput());
    }

    @Test
    public void fromActionResponse_Success_WithNonMLForwardResponse() {
        MLForwardResponse response = new MLForwardResponse(status, predictionOutput);
        ActionResponse actionResponse = new ActionResponse() {

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLForwardResponse result = MLForwardResponse.fromActionResponse(actionResponse);
        assertNotSame(result, response);
        assertEquals(response.getStatus(), result.getStatus());
        assertEquals(response.getMlOutput().toString(), result.getMlOutput().toString());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLForwardResponse.fromActionResponse(actionResponse);
    }

}
