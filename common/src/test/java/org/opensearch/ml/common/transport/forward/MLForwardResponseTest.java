package org.opensearch.ml.common.transport.forward;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.output.MLPredictionOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class MLForwardResponseTest {

    @Mock
    MLPredictionOutput predictionOutput;
    DataFrame dataFrame;
    String status;

    @Before
    public void setUp() throws Exception {
        status = "test";
        predictionOutput = MLPredictionOutput.builder()
                .status("Success")
                .predictionResult(dataFrame)
                .taskId("taskId")
                .build();
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
        assertEquals(response.getMlOutput(), parsedResponse.getMlOutput());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLForwardResponse response = new MLForwardResponse(status, predictionOutput);
        // Run the test
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        // Verify the results
        assertEquals("{\"result\":{\"task_id\":\"taskId\",\"status\":\"Success\"}}", jsonStr);
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
    public void fromActionResponse_Success_WithNonMLUploadModelRequest() {
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
        assertEquals(response.getMlOutput(), result.getMlOutput());
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
