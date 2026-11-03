/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.output.execute.agent.AgentV2Output.MEMORY_ID_FIELD;
import static org.opensearch.ml.common.output.execute.agent.AgentV2Output.MESSAGE_FIELD;
import static org.opensearch.ml.common.output.execute.agent.AgentV2Output.METRICS_FIELD;
import static org.opensearch.ml.common.output.execute.agent.AgentV2Output.STOP_REASON_FIELD;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.output.MLOutputType;

public class AgentV2OutputTest {

    private ContentBlock createContentBlock(String text) {
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setText(text);
        return contentBlock;
    }

    private Message createMessage(String role, List<ContentBlock> content) {
        return new Message(role, content);
    }

    @Test
    public void testBuilder() {
        ContentBlock contentBlock = createContentBlock("Hello, how can I help you?");
        Message message = createMessage("assistant", List.of(contentBlock));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("input_tokens", 100L);
        metrics.put("output_tokens", 50L);

        AgentV2Output output = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(message)
            .memoryId("memory-123")
            .metrics(metrics)
            .build();

        assertNotNull(output);
        assertEquals("end_turn", output.getStopReason());
        assertEquals(message, output.getMessage());
        assertEquals("memory-123", output.getMemoryId());
        assertEquals(metrics, output.getMetrics());
        assertEquals(MLOutputType.AGENT_V2, output.getType());
    }

    @Test
    public void testBuilderWithNullValues() {
        AgentV2Output output = AgentV2Output.builder().stopReason(null).message(null).memoryId(null).metrics(null).build();

        assertNotNull(output);
        assertNull(output.getStopReason());
        assertNull(output.getMessage());
        assertNull(output.getMemoryId());
        assertNotNull(output.getMetrics()); // Should default to empty map
        assertTrue(output.getMetrics().isEmpty());
    }

    @Test
    public void testBuilderWithEmptyMetrics() {
        AgentV2Output output = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(null)
            .memoryId("mem-1")
            .metrics(new HashMap<>())
            .build();

        assertNotNull(output);
        assertNotNull(output.getMetrics());
        assertTrue(output.getMetrics().isEmpty());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        ContentBlock contentBlock = createContentBlock("Test response");
        Message message = createMessage("assistant", List.of(contentBlock));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("input_tokens", 150L);
        metrics.put("output_tokens", 75L);
        metrics.put("latency_ms", 1200L);

        AgentV2Output original = AgentV2Output
            .builder()
            .stopReason("max_iterations")
            .message(message)
            .memoryId("conv-456")
            .metrics(metrics)
            .build();

        // Serialize
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        // Deserialize - must read enum first
        StreamInput input = output.bytes().streamInput();
        MLOutputType outputType = input.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.AGENT_V2, outputType);
        AgentV2Output deserialized = new AgentV2Output(input);

        // Verify
        assertEquals(original.getStopReason(), deserialized.getStopReason());
        assertEquals(original.getMemoryId(), deserialized.getMemoryId());
        assertEquals(original.getMetrics(), deserialized.getMetrics());
        assertNotNull(deserialized.getMessage());
        assertEquals(original.getMessage().getRole(), deserialized.getMessage().getRole());
        assertEquals(original.getMessage().getContent().size(), deserialized.getMessage().getContent().size());
        assertEquals(original.getMessage().getContent().get(0).getText(), deserialized.getMessage().getContent().get(0).getText());
    }

    @Test
    public void testStreamSerializationWithNullMessage() throws IOException {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("error", "Failed to execute");

        AgentV2Output original = AgentV2Output.builder().stopReason("error").message(null).memoryId("conv-789").metrics(metrics).build();

        // Serialize
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        // Deserialize - must read enum first
        StreamInput input = output.bytes().streamInput();
        MLOutputType outputType = input.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.AGENT_V2, outputType);
        AgentV2Output deserialized = new AgentV2Output(input);

        // Verify
        assertEquals(original.getStopReason(), deserialized.getStopReason());
        assertNull(deserialized.getMessage());
        assertEquals(original.getMemoryId(), deserialized.getMemoryId());
        assertEquals(original.getMetrics(), deserialized.getMetrics());
    }

    @Test
    public void testStreamSerializationWithEmptyMetrics() throws IOException {
        ContentBlock contentBlock = createContentBlock("Simple response");
        Message message = createMessage("assistant", List.of(contentBlock));

        AgentV2Output original = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(message)
            .memoryId("conv-001")
            .metrics(new HashMap<>())
            .build();

        // Serialize
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        // Deserialize - must read enum first
        StreamInput input = output.bytes().streamInput();
        MLOutputType outputType = input.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.AGENT_V2, outputType);
        AgentV2Output deserialized = new AgentV2Output(input);

        // Verify
        assertEquals(original.getStopReason(), deserialized.getStopReason());
        assertEquals(original.getMemoryId(), deserialized.getMemoryId());
        assertNotNull(deserialized.getMetrics());
        assertTrue(deserialized.getMetrics().isEmpty());
    }

    @Test
    public void testToXContent() throws IOException {
        ContentBlock contentBlock = createContentBlock("Response text");
        Message message = createMessage("assistant", List.of(contentBlock));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("input_tokens", 200L);
        metrics.put("output_tokens", 100L);

        AgentV2Output output = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(message)
            .memoryId("session-123")
            .metrics(metrics)
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains(STOP_REASON_FIELD));
        assertTrue(jsonStr.contains("end_turn"));
        assertTrue(jsonStr.contains(MESSAGE_FIELD));
        assertTrue(jsonStr.contains("Response text"));
        assertTrue(jsonStr.contains(MEMORY_ID_FIELD));
        assertTrue(jsonStr.contains("session-123"));
        assertTrue(jsonStr.contains(METRICS_FIELD));
        assertTrue(jsonStr.contains("input_tokens"));
        assertTrue(jsonStr.contains("output_tokens"));
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        AgentV2Output output = AgentV2Output.builder().stopReason(null).message(null).memoryId(null).metrics(new HashMap<>()).build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertNotNull(jsonStr);
        // Should be a valid JSON object
        assertTrue(jsonStr.startsWith("{"));
        assertTrue(jsonStr.endsWith("}"));
    }

    @Test
    public void testToXContentWithMultipleContentBlocks() throws IOException {
        ContentBlock contentBlock1 = createContentBlock("First part of response");
        ContentBlock contentBlock2 = createContentBlock("Second part of response");
        Message message = createMessage("assistant", List.of(contentBlock1, contentBlock2));

        AgentV2Output output = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(message)
            .memoryId("conv-456")
            .metrics(new HashMap<>())
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains("First part of response"));
        assertTrue(jsonStr.contains("Second part of response"));
    }

    @Test
    public void testToXContentWithEmptyContent() throws IOException {
        Message message = createMessage("assistant", List.of());

        AgentV2Output output = AgentV2Output
            .builder()
            .stopReason("end_turn")
            .message(message)
            .memoryId("conv-789")
            .metrics(new HashMap<>())
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains(MESSAGE_FIELD));
    }

    @Test
    public void testGetType() {
        AgentV2Output output = AgentV2Output.builder().stopReason("end_turn").message(null).memoryId("conv-1").build();

        assertEquals(MLOutputType.AGENT_V2, output.getType());
    }

    @Test
    public void testStopReasonEndTurn() {
        AgentV2Output output = AgentV2Output.builder().stopReason("end_turn").build();
        assertEquals("end_turn", output.getStopReason());
    }

    @Test
    public void testStopReasonMaxIterations() {
        AgentV2Output output = AgentV2Output.builder().stopReason("max_iterations").build();
        assertEquals("max_iterations", output.getStopReason());
    }

    @Test
    public void testStopReasonToolUse() {
        AgentV2Output output = AgentV2Output.builder().stopReason("tool_use").build();
        assertEquals("tool_use", output.getStopReason());
    }

    @Test
    public void testComplexMetrics() throws IOException {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_tokens", 500L);
        metrics.put("input_tokens", 300L);
        metrics.put("output_tokens", 200L);
        metrics.put("latency_ms", 2500L);
        metrics.put("model_id", "claude-3-sonnet");

        AgentV2Output output = AgentV2Output.builder().stopReason("end_turn").memoryId("conv-999").metrics(metrics).build();

        // Test serialization
        BytesStreamOutput streamOutput = new BytesStreamOutput();
        output.writeTo(streamOutput);

        StreamInput input = streamOutput.bytes().streamInput();
        MLOutputType outputType = input.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.AGENT_V2, outputType);
        AgentV2Output deserialized = new AgentV2Output(input);

        assertEquals(5, deserialized.getMetrics().size());
        assertEquals(500L, deserialized.getMetrics().get("total_tokens"));
        assertEquals("claude-3-sonnet", deserialized.getMetrics().get("model_id"));
    }

    @Test
    public void testMessageWithRole() throws IOException {
        ContentBlock contentBlock = createContentBlock("Test content");
        Message message = createMessage("assistant", List.of(contentBlock));

        AgentV2Output output = AgentV2Output.builder().stopReason("end_turn").message(message).memoryId("conv-1").build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"role\":\"assistant\""));
    }

    @Test
    public void testRoundTripSerialization() throws IOException {
        // Create a complex output
        ContentBlock contentBlock = createContentBlock("Complex response with multiple fields");
        Message message = createMessage("assistant", List.of(contentBlock));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("input_tokens", 250L);
        metrics.put("output_tokens", 125L);
        metrics.put("tool_calls", 3L);

        AgentV2Output original = AgentV2Output
            .builder()
            .stopReason("tool_use")
            .message(message)
            .memoryId("session-abc-123")
            .metrics(metrics)
            .build();

        // Serialize to bytes
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        // Deserialize from bytes - must read enum first
        StreamInput input = output.bytes().streamInput();
        MLOutputType outputType = input.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.AGENT_V2, outputType);
        AgentV2Output deserialized = new AgentV2Output(input);

        // Verify fields match (don't compare JSON strings due to HashMap ordering)
        assertEquals(original.getStopReason(), deserialized.getStopReason());
        assertEquals(original.getMemoryId(), deserialized.getMemoryId());
        assertEquals(original.getMessage().getRole(), deserialized.getMessage().getRole());
        assertEquals(original.getMessage().getContent().size(), deserialized.getMessage().getContent().size());
        assertEquals(original.getMetrics().size(), deserialized.getMetrics().size());
        assertEquals(original.getMetrics().get("input_tokens"), deserialized.getMetrics().get("input_tokens"));
        assertEquals(original.getMetrics().get("output_tokens"), deserialized.getMetrics().get("output_tokens"));
        assertEquals(original.getMetrics().get("tool_calls"), deserialized.getMetrics().get("tool_calls"));
    }

    @Test
    public void testSettersAndGetters() {
        AgentV2Output output = AgentV2Output.builder().build();

        // Test setters
        output.setStopReason("max_iterations");
        assertEquals("max_iterations", output.getStopReason());

        output.setMemoryId("new-memory-id");
        assertEquals("new-memory-id", output.getMemoryId());

        ContentBlock contentBlock = createContentBlock("New content");
        Message message = createMessage("user", List.of(contentBlock));
        output.setMessage(message);
        assertEquals(message, output.getMessage());

        Map<String, Object> newMetrics = new HashMap<>();
        newMetrics.put("custom_metric", "value");
        output.setMetrics(newMetrics);
        assertEquals(newMetrics, output.getMetrics());
    }

    @Test
    public void testEqualsAndHashCode() {
        ContentBlock contentBlock = createContentBlock("Same content");
        Message message = createMessage("assistant", List.of(contentBlock));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("tokens", 100L);

        AgentV2Output output1 = AgentV2Output.builder().stopReason("end_turn").message(message).memoryId("mem-1").metrics(metrics).build();

        AgentV2Output output2 = AgentV2Output.builder().stopReason("end_turn").message(message).memoryId("mem-1").metrics(metrics).build();

        assertEquals(output1, output2);
        assertEquals(output1.hashCode(), output2.hashCode());
    }

    @Test
    public void testToString() {
        ContentBlock contentBlock = createContentBlock("Test output");
        Message message = createMessage("assistant", List.of(contentBlock));

        AgentV2Output output = AgentV2Output.builder().stopReason("end_turn").message(message).memoryId("mem-test").build();

        String str = output.toString();
        assertNotNull(str);
        assertTrue(str.contains("AgentV2Output"));
    }
}
