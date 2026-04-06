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

public class MLHybridSearchMemoriesInputTest {

    @Test
    public void testConstructorAndGetters() {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test query")
            .k(5)
            .namespace(Map.of("user_id", "bob"))
            .tags(Map.of("topic", "devops"))
            .build();

        assertEquals("c1", input.getMemoryContainerId());
        assertEquals("test query", input.getQuery());
        assertEquals(5, input.getK());
        assertEquals("bob", input.getNamespace().get("user_id"));
        assertEquals("devops", input.getTags().get("topic"));
    }

    @Test
    public void testDefaultK() {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        assertEquals(10, input.getK());
    }

    @Test
    public void testNullQueryThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query(null).build()
        );
    }

    @Test
    public void testBlankQueryThrows() {
        assertThrows(IllegalArgumentException.class, () -> MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("").build());
    }

    @Test
    public void testSerialization() throws IOException {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId("c1")
            .query("test")
            .k(3)
            .namespace(Map.of("user_id", "alice"))
            .tags(Map.of("topic", "food"))
            .minScore(0.5f)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLHybridSearchMemoriesInput deserialized = new MLHybridSearchMemoriesInput(in);

        assertEquals("c1", deserialized.getMemoryContainerId());
        assertEquals("test", deserialized.getQuery());
        assertEquals(3, deserialized.getK());
        assertEquals("alice", deserialized.getNamespace().get("user_id"));
        assertEquals("food", deserialized.getTags().get("topic"));
        assertEquals(0.5f, deserialized.getMinScore(), 0.001);
    }

    @Test
    public void testSerializationWithNulls() throws IOException {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput
            .builder()
            .memoryContainerId(null)
            .query("test")
            .namespace(null)
            .tags(null)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLHybridSearchMemoriesInput deserialized = new MLHybridSearchMemoriesInput(in);

        assertNull(deserialized.getMemoryContainerId());
        assertNull(deserialized.getNamespace());
        assertNull(deserialized.getTags());
    }
}
