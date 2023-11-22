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

public class MLRegisterModelGroupResponseTest {

    MLRegisterModelGroupResponse mlRegisterModelGroupResponse;

    @Before
    public void setup() {
        mlRegisterModelGroupResponse = new MLRegisterModelGroupResponse("ModelGroupId", "Status");
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlRegisterModelGroupResponse.writeTo(bytesStreamOutput);
        MLRegisterModelGroupResponse newResponse = new MLRegisterModelGroupResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlRegisterModelGroupResponse.getModelGroupId(), newResponse.getModelGroupId());
        assertEquals(mlRegisterModelGroupResponse.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        MLRegisterModelGroupResponse response = new MLRegisterModelGroupResponse("ModelGroupId", "Status");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"model_group_id\":\"ModelGroupId\",\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }
}
