/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;


import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MLCreateModelControllerResponseTest {

    private MLCreateModelControllerResponse response;

    @Before
    public void setup() {
        response = new MLCreateModelControllerResponse("testModelId", "Status");
    }


    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        MLCreateModelControllerResponse newResponse = new MLCreateModelControllerResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals(response.getModelId(), newResponse.getModelId());
        assertEquals(response.getStatus(), newResponse.getStatus());
    }

    @Test
    public void testToXContent() throws IOException {
        MLCreateModelControllerResponse response = new MLCreateModelControllerResponse("testModelId", "Status");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = TestHelper.xContentBuilderToString(builder);
        final String expected = "{\"model_id\":\"testModelId\",\"status\":\"Status\"}";
        assertEquals(expected, jsonStr);
    }

    @Test
    public void fromActionResponseWithMLCreateModelControllerResponseSuccess() {
        MLCreateModelControllerResponse responseFromActionResponse = MLCreateModelControllerResponse.fromActionResponse(response);
        assertSame(response, responseFromActionResponse);
        assertEquals(response.getModelId(), responseFromActionResponse.getModelId());
    }

    @Test
    public void fromActionResponseSuccess() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLCreateModelControllerResponse responseFromActionResponse = MLCreateModelControllerResponse.fromActionResponse(actionResponse);
        assertNotSame(response, responseFromActionResponse);
        assertEquals(response.getModelId(), responseFromActionResponse.getModelId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLCreateModelControllerResponse.fromActionResponse(actionResponse);
    }
}
