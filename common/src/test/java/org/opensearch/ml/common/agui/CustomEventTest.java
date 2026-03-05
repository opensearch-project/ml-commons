/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class CustomEventTest {

    @Test
    public void testConstructor_setsTypeAndFields() {
        Map<String, Object> value = new HashMap<>();
        value.put("inputTokens", 100);
        value.put("outputTokens", 50);
        value.put("totalTokens", 150);

        CustomEvent event = new CustomEvent("token_usage", value);

        assertEquals(CustomEvent.TYPE, event.getType());
        assertEquals("Custom", event.getType());
        assertEquals("token_usage", event.getName());
        assertNotNull(event.getValue());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testConstructor_nullValue() {
        CustomEvent event = new CustomEvent("token_usage", null);

        assertEquals(CustomEvent.TYPE, event.getType());
        assertEquals("token_usage", event.getName());
        assertNull(event.getValue());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testConstructor_timestampIsRecent() {
        long before = System.currentTimeMillis();
        CustomEvent event = new CustomEvent("token_usage", null);
        long after = System.currentTimeMillis();

        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }

    @Test
    public void testToJsonString_containsRequiredFields() {
        Map<String, Object> value = new HashMap<>();
        value.put("inputTokens", 100);
        value.put("outputTokens", 50);

        CustomEvent event = new CustomEvent("token_usage", value);
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("Custom"));
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("token_usage"));
        assertTrue(json.contains("\"value\""));
        assertTrue(json.contains("inputTokens"));
        assertTrue(json.contains("100"));
    }

    @Test
    public void testToJsonString_nullValueOmitted() {
        CustomEvent event = new CustomEvent("token_usage", null);
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("Custom"));
        assertTrue(json.contains("token_usage"));
        // value field should not appear when null
        assertTrue(!json.contains("\"value\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSerialization_roundTrip() throws IOException {
        Map<String, Object> value = new HashMap<>();
        value.put("inputTokens", 100);
        value.put("outputTokens", 50);
        value.put("totalTokens", 150);

        CustomEvent original = new CustomEvent("token_usage", value);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        CustomEvent deserialized = new CustomEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertNotNull(deserialized.getValue());

        Map<String, Object> deserializedValue = (Map<String, Object>) deserialized.getValue();
        assertEquals(((Map<String, Object>) original.getValue()).get("inputTokens"), deserializedValue.get("inputTokens"));
        assertEquals(((Map<String, Object>) original.getValue()).get("outputTokens"), deserializedValue.get("outputTokens"));
        assertEquals(((Map<String, Object>) original.getValue()).get("totalTokens"), deserializedValue.get("totalTokens"));
    }

    @Test
    public void testSerialization_nullValueRoundTrip() throws IOException {
        CustomEvent original = new CustomEvent("token_usage", null);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        CustomEvent deserialized = new CustomEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getName(), deserialized.getName());
        assertNull(deserialized.getValue());
    }

    @Test
    public void testSerialization_customNameRoundTrip() throws IOException {
        CustomEvent original = new CustomEvent("my_custom_event", Map.of("key", "val"));

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput streamInput = output.bytes().streamInput();
        CustomEvent deserialized = new CustomEvent(streamInput);

        assertEquals("my_custom_event", deserialized.getName());
        assertEquals(CustomEvent.TYPE, deserialized.getType());
    }

    @Test
    public void testNoArgsConstructor() {
        CustomEvent event = new CustomEvent();

        assertNull(event.getName());
        assertNull(event.getValue());
    }
}
