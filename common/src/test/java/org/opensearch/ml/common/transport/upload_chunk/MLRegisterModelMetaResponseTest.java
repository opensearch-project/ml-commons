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

public class MLRegisterModelMetaResponseTest {

    MLRegisterModelMetaResponse mlRegisterModelMetaResponse;

    @Before
    public void setup() {
        mlRegisterModelMetaResponse = new MLRegisterModelMetaResponse("Model Id", "Status");
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlRegisterModelMetaResponse.writeTo(bytesStreamOutput);
        MLRegisterModelMetaResponse newResponse = new MLRegisterModelMetaResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlRegisterModelMetaResponse.getModelId(), newResponse.getModelId());
        assertEquals(mlRegisterModelMetaResponse.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        MLRegisterModelMetaResponse response = new MLRegisterModelMetaResponse("Model Id", "Status");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"model_id\":\"Model Id\",\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }
}
