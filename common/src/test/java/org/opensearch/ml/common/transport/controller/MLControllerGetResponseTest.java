/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.controller.MLRateLimiter;

public class MLControllerGetResponseTest {

    private MLController controller;

    private MLControllerGetResponse response;

    @Before
    public void setUp() {
        MLRateLimiter rateLimiter = MLRateLimiter.builder()
                .limit("1")
                .unit(TimeUnit.MILLISECONDS)
                .build();
        controller = MLController.builder()
                .modelId("testModelId")
                .userRateLimiter(new HashMap<>() {
                    {
                        put("testUser", rateLimiter);
                    }
                })
                .build();
        response = MLControllerGetResponse.builder().controller(controller).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        MLControllerGetResponse parsedResponse = new MLControllerGetResponse(
                bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.getController(), parsedResponse.getController());
        assertEquals(response.getController().getModelId(), parsedResponse.getController().getModelId());
        assertEquals(response.getController().getUserRateLimiter().get("testUser").getLimit(),
                parsedResponse.getController().getUserRateLimiter().get("testUser").getLimit());
        assertEquals(response.getController().getUserRateLimiter().get("testUser").getUnit(),
                parsedResponse.getController().getUserRateLimiter().get("testUser").getUnit());
    }

    @Test
    public void toXContentTest() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(
                "{\"model_id\":\"testModelId\",\"user_rate_limiter\":{\"testUser\":{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"}}}",
                jsonStr);
    }

    @Test
    public void fromActionResponseWithMLControllerGetResponseSuccess() {
        MLControllerGetResponse responseFromActionResponse = MLControllerGetResponse
                .fromActionResponse(response);
        assertSame(response, responseFromActionResponse);
        assertEquals(response.getController(), responseFromActionResponse.getController());
    }

    @Test
    public void fromActionResponseSuccess() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLControllerGetResponse responseFromActionResponse = MLControllerGetResponse
                .fromActionResponse(actionResponse);
        assertNotSame(response, responseFromActionResponse);
        assertNotEquals(response.getController(), responseFromActionResponse.getController());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLControllerGetResponse.fromActionResponse(actionResponse);
    }
}
