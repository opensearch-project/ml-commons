package org.opensearch.ml.common.transport.sync;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class MLSyncUpResponseTest {
    private String status;

    @Before
    public void setUp() throws Exception {
        status = "test";
    }

    @Test
    public void writeTo_Success() throws IOException {
        // Setup
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLSyncUpResponse response = new MLSyncUpResponse(status);
        // Run the test
        response.writeTo(bytesStreamOutput);
        MLSyncUpResponse parsedResponse = new MLSyncUpResponse(bytesStreamOutput.bytes().streamInput());
        // Verify the results
        assertEquals(response.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        // Setup
        MLSyncUpResponse response = new MLSyncUpResponse(status);
        // Run the test
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        // Verify the results
        assertEquals("{\"status\":\"test\"}", jsonStr);
    }
}
