/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLSemanticSearchMemoriesInputTest {

    @Test
    public void testConstructorAndGetters() {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test query")
            .k(5)
            .namespace(Map.of("user_id", "alice"))
            .tags(Map.of("topic", "programming"))
            .minScore(0.7f)
            .build();

        assertEquals("c1", input.getMemoryContainerId());
        assertEquals("test query", input.getQuery());
        assertEquals(5, input.getK());
        assertEquals("alice", input.getNamespace().get("user_id"));
        assertEquals("programming", input.getTags().get("topic"));
        assertEquals(0.7f, input.getMinScore(), 0.001);
    }

    @Test
    public void testDefaultK() {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        assertEquals(10, input.getK());
    }

    @Test
    public void testNullQueryThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query(null).build()
        );
    }

    @Test
    public void testBlankQueryThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query("  ").build()
        );
    }

    @Test
    public void testSerialization() throws IOException {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test query")
            .k(5)
            .namespace(Map.of("user_id", "alice"))
            .tags(Map.of("topic", "food"))
            .minScore(0.8f)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSemanticSearchMemoriesInput deserialized = new MLSemanticSearchMemoriesInput(in);

        assertEquals("c1", deserialized.getMemoryContainerId());
        assertEquals("test query", deserialized.getQuery());
        assertEquals(5, deserialized.getK());
        assertEquals("alice", deserialized.getNamespace().get("user_id"));
        assertEquals("food", deserialized.getTags().get("topic"));
        assertEquals(0.8f, deserialized.getMinScore(), 0.001);
    }

    @Test
    public void testSerializationWithNulls() throws IOException {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput
            .builder()
            .memoryContainerId(null)
            .query("test")
            .k(10)
            .namespace(null)
            .tags(null)
            .minScore(null)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSemanticSearchMemoriesInput deserialized = new MLSemanticSearchMemoriesInput(in);

        assertNull(deserialized.getMemoryContainerId());
        assertNull(deserialized.getNamespace());
        assertNull(deserialized.getTags());
        assertNull(deserialized.getMinScore());
    }
}
