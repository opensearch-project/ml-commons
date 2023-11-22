/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MLUploadModelChunkResponseTest {

    MLUploadModelChunkResponse mlUploadModelChunkResponse;

    @Before
    public void setup() {
        mlUploadModelChunkResponse = new MLUploadModelChunkResponse("Status");
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUploadModelChunkResponse.writeTo(bytesStreamOutput);
        MLUploadModelChunkResponse newResponse = new MLUploadModelChunkResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlUploadModelChunkResponse.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlUploadModelChunkResponse.toXContent(builder, EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }
}
