package org.opensearch.ml.common.transport.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLCancelBatchJobResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLCancelBatchJobResponse response = MLCancelBatchJobResponse.builder().status(RestStatus.OK).build();
        response.writeTo(bytesStreamOutput);
        MLCancelBatchJobResponse parsedResponse = new MLCancelBatchJobResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(response.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLCancelBatchJobResponse mlCancelBatchJobResponse1 = MLCancelBatchJobResponse.builder().status(RestStatus.OK).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlCancelBatchJobResponse1.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"status\":\"OK\"}", jsonStr);
    }
}
