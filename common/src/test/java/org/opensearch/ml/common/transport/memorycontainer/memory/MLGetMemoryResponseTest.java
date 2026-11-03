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
import org.opensearch.ml.common.memorycontainer.MLLongTermMemory;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;

public class MLGetMemoryResponseTest {

    private MLGetMemoryResponse responseNormal;
    private MLLongTermMemory testMemory;

    @Before
    public void setUp() {
        testMemory = MLLongTermMemory
            .builder()
            .memory("Test memory content")
            .strategyType(MemoryStrategyType.SEMANTIC)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        responseNormal = MLGetMemoryResponse.builder().longTermMemory(testMemory).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(responseNormal);
        assertNotNull(responseNormal.getLongTermMemory());
        assertEquals("Test memory content", responseNormal.getLongTermMemory().getMemory());
        assertEquals(MemoryStrategyType.SEMANTIC, responseNormal.getLongTermMemory().getStrategyType());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        responseNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLGetMemoryResponse deserialized = new MLGetMemoryResponse(in);

        assertNotNull(deserialized.getLongTermMemory());
        assertEquals(responseNormal.getLongTermMemory().getMemory(), deserialized.getLongTermMemory().getMemory());
        assertEquals(responseNormal.getLongTermMemory().getStrategyType(), deserialized.getLongTermMemory().getStrategyType());
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
                // Write session (false)
                out.writeBoolean(false);
                // Write workingMemory (false)
                out.writeBoolean(false);
                // Write longTermMemory (true)
                out.writeBoolean(true);
                testMemory.writeTo(out);
                // Write memoryHistory (false)
                out.writeBoolean(false);
            }
        };

        MLGetMemoryResponse result = MLGetMemoryResponse.fromActionResponse(mockResponse);
        assertNotNull(result);
        assertNotNull(result.getLongTermMemory());
        assertEquals("Test memory content", result.getLongTermMemory().getMemory());
        assertEquals(MemoryStrategyType.SEMANTIC, result.getLongTermMemory().getStrategyType());
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
        assertTrue(jsonString.contains("Test memory content"));
        assertTrue(jsonString.contains("SEMANTIC"));
    }
}
