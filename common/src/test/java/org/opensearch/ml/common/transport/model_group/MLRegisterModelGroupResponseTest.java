/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class MLRegisterModelGroupResponseTest {

    MLRegisterModelGroupResponse response;

    @Before
    public void setup() {
        response = new MLRegisterModelGroupResponse("testModelGroupId", "Status");
    }


    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        MLRegisterModelGroupResponse newResponse = new MLRegisterModelGroupResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(response.getModelGroupId(), newResponse.getModelGroupId());
        assertEquals(response.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        MLRegisterModelGroupResponse response = new MLRegisterModelGroupResponse("testModelGroupId", "Status");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"model_group_id\":\"testModelGroupId\",\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }

    @Test
    public void fromActionResponseWithMLRegisterModelGroupResponseSuccess() {
        MLRegisterModelGroupResponse responseFromActionResponse = MLRegisterModelGroupResponse.fromActionResponse(response);
        assertSame(response, responseFromActionResponse);
        assertEquals(response.getModelGroupId(), responseFromActionResponse.getModelGroupId());
    }

    @Test
    public void fromActionResponseSuccess() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLRegisterModelGroupResponse responseFromActionResponse = MLRegisterModelGroupResponse.fromActionResponse(actionResponse);
        assertNotSame(response, responseFromActionResponse);
        assertEquals(response.getModelGroupId(), responseFromActionResponse.getModelGroupId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLRegisterModelGroupResponse.fromActionResponse(actionResponse);
    }
}
