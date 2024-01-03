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
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.controller.MLRateLimiter;

public class MLModelControllerGetResponseTest {

    private MLModelController modelController;

    private MLModelControllerGetResponse response;

    @Before
    public void setUp() {
        MLRateLimiter rateLimiter = MLRateLimiter.builder()
                .rateLimitNumber("1")
                .rateLimitUnit(TimeUnit.MILLISECONDS)
                .build();
        modelController = MLModelController.builder()
                .modelId("testModelId")
                .userRateLimiterConfig(new HashMap<>() {{
                    put("testUser", rateLimiter);
                }})
                .build();
        response = MLModelControllerGetResponse.builder().modelController(modelController).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        MLModelControllerGetResponse parsedResponse = new MLModelControllerGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.getModelController(), parsedResponse.getModelController());
        assertEquals(response.getModelController().getModelId(), parsedResponse.getModelController().getModelId());
        assertEquals(response.getModelController().getUserRateLimiterConfig().get("testUser").getRateLimitNumber(), parsedResponse.getModelController().getUserRateLimiterConfig().get("testUser").getRateLimitNumber());
        assertEquals(response.getModelController().getUserRateLimiterConfig().get("testUser").getRateLimitUnit(), parsedResponse.getModelController().getUserRateLimiterConfig().get("testUser").getRateLimitUnit());
    }

    @Test
    public void toXContentTest() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"model_id\":\"testModelId\",\"user_rate_limiter_config\":{\"testUser\":{\"rate_limit_number\":\"1\",\"rate_limit_unit\":\"MILLISECONDS\"}}}",jsonStr);
    }

    @Test
    public void fromActionResponseWithMLModelControllerGetResponseSuccess() {
        MLModelControllerGetResponse responseFromActionResponse = MLModelControllerGetResponse.fromActionResponse(response);
        assertSame(response, responseFromActionResponse);
        assertEquals(response.getModelController(), responseFromActionResponse.getModelController());
    }

    @Test
    public void fromActionResponseSuccess() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLModelControllerGetResponse responseFromActionResponse = MLModelControllerGetResponse.fromActionResponse(actionResponse);
        assertNotSame(response, responseFromActionResponse);
        assertNotEquals(response.getModelController(), responseFromActionResponse.getModelController());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLModelControllerGetResponse.fromActionResponse(actionResponse);
    }
}
