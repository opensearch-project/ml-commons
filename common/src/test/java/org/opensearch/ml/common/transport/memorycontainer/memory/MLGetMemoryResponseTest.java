/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MemoryType;

public class MLGetMemoryResponseTest {

    private MLGetMemoryResponse responseNormal;
    private MLMemory testMemory;

    @Before
    public void setUp() {
        testMemory = MLMemory
            .builder()
            .sessionId("test-session")
            .memory("Test memory content")
            .memoryType(MemoryType.RAW_MESSAGE)
            .userId("test-user")
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        responseNormal = MLGetMemoryResponse.builder().mlMemory(testMemory).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(responseNormal);
        assertNotNull(responseNormal.getMlMemory());
        assertEquals("test-session", responseNormal.getMlMemory().getSessionId());
        assertEquals("Test memory content", responseNormal.getMlMemory().getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, responseNormal.getMlMemory().getMemoryType());
        assertEquals("test-user", responseNormal.getMlMemory().getUserId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        responseNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLGetMemoryResponse deserialized = new MLGetMemoryResponse(in);

        assertNotNull(deserialized.getMlMemory());
        assertEquals(responseNormal.getMlMemory().getSessionId(), deserialized.getMlMemory().getSessionId());
        assertEquals(responseNormal.getMlMemory().getMemory(), deserialized.getMlMemory().getMemory());
        assertEquals(responseNormal.getMlMemory().getMemoryType(), deserialized.getMlMemory().getMemoryType());
        assertEquals(responseNormal.getMlMemory().getUserId(), deserialized.getMlMemory().getUserId());
    }

    @Test
    public void testFromActionResponseSameInstance() {
        MLGetMemoryResponse result = MLGetMemoryResponse.fromActionResponse(responseNormal);
        assertEquals(responseNormal, result);
    }

    @Test
    public void testFromActionResponseDifferentInstance() throws IOException {
        // Create a mock ActionResponse that's not MLGetMemoryResponse
        ActionResponse mockResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                testMemory.writeTo(out);
            }
        };

        MLGetMemoryResponse result = MLGetMemoryResponse.fromActionResponse(mockResponse);
        assertNotNull(result);
        assertNotNull(result.getMlMemory());
        assertEquals("test-session", result.getMlMemory().getSessionId());
        assertEquals("Test memory content", result.getMlMemory().getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, result.getMlMemory().getMemoryType());
        assertEquals("test-user", result.getMlMemory().getUserId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionResponseIOException() {
        // Create a mock ActionResponse that throws IOException
        ActionResponse mockResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test exception");
            }
        };

        MLGetMemoryResponse.fromActionResponse(mockResponse);
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseNormal.toXContent(builder, null);
        String jsonString = builder.toString();

        assertNotNull(jsonString);
        assertTrue(jsonString.contains("test-session"));
        assertTrue(jsonString.contains("Test memory content"));
        assertTrue(jsonString.contains("RAW_MESSAGE"));
        assertTrue(jsonString.contains("test-user"));
    }
}
