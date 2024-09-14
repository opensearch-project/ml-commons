/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

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

public class MLUpdateModelGroupResponseTest {

    MLUpdateModelGroupResponse mlUpdateModelGroupResponse;

    @Before
    public void setup() {
        mlUpdateModelGroupResponse = new MLUpdateModelGroupResponse("Status");
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUpdateModelGroupResponse.writeTo(bytesStreamOutput);
        MLUpdateModelGroupResponse newResponse = new MLUpdateModelGroupResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlUpdateModelGroupResponse.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        MLUpdateModelGroupResponse response = new MLUpdateModelGroupResponse("Status");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }
}
