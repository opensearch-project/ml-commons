/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class ClaudeInvokeModelEventParserTest {

    private ClaudeInvokeModelEventParser parser;

    @Before
    public void setUp() {
        parser = new ClaudeInvokeModelEventParser();
    }

    @Test
    public void testParseMessageStart() {
        String json = "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_START, event.getType());
    }

    @Test
    public void testParseContentBlockStartText() {
        String json = "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_START_TEXT, event.getType());
        assertEquals(0, event.getIndex());
    }

    @Test
    public void testParseContentBlockStartToolUse() {
        String json =
            "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_123\",\"name\":\"get_weather\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_START_TOOL_USE, event.getType());
        assertEquals(1, event.getIndex());
        assertEquals("toolu_123", event.getToolUseId());
        assertEquals("get_weather", event.getToolName());
    }

    @Test
    public void testParseContentBlockStartCompaction() {
        String json = "{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"compaction\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_START_COMPACTION, event.getType());
        assertEquals(2, event.getIndex());
    }

    @Test
    public void testParseTextDelta() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello, world!\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TEXT_DELTA, event.getType());
        assertEquals("Hello, world!", event.getText());
        assertEquals(0, event.getIndex());
    }

    @Test
    public void testParseToolInputDelta() {
        String json =
            "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"location\\\"\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TOOL_INPUT_DELTA, event.getType());
        assertEquals("{\"location\"", event.getToolInputJson());
        assertEquals(1, event.getIndex());
    }

    @Test
    public void testParseContentBlockStop() {
        String json = "{\"type\":\"content_block_stop\",\"index\":0}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_STOP, event.getType());
        assertEquals(0, event.getIndex());
    }

    @Test
    public void testParseMessageDeltaEndTurn() {
        String json = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_DELTA, event.getType());
        assertEquals("end_turn", event.getStopReason());
    }

    @Test
    public void testParseMessageDeltaToolUse() {
        String json = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_DELTA, event.getType());
        assertEquals("tool_use", event.getStopReason());
    }

    @Test
    public void testParseMessageDeltaCompaction() {
        String json = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"compaction\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_DELTA, event.getType());
        assertEquals("compaction", event.getStopReason());
    }

    @Test
    public void testParseMessageStop() {
        String json = "{\"type\":\"message_stop\"}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_STOP, event.getType());
    }

    @Test
    public void testParseUnknownEventType() {
        String json = "{\"type\":\"ping\"}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNull(event);
    }

    @Test
    public void testParseUnknownContentBlockType() {
        String json = "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"unknown_type\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNull(event);
    }

    @Test
    public void testParseUnknownDeltaType() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"unknown_delta\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNull(event);
    }

    @Test
    public void testParseMalformedJson() {
        String json = "not valid json{{{";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNull(event);
    }

    @Test
    public void testParseEmptyJson() {
        String json = "{}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        // Empty type string won't match any case -> returns null
        assertNull(event);
    }

    @Test
    public void testParseTextDeltaWithSpecialCharacters() {
        String json =
            "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\\n\\\"world\\\"\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TEXT_DELTA, event.getType());
        assertEquals("Hello\n\"world\"", event.getText());
    }

    @Test
    public void testParseMessageDeltaWithNullStopReason() {
        String json = "{\"type\":\"message_delta\",\"delta\":{}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.MESSAGE_DELTA, event.getType());
        assertNull(event.getStopReason());
    }

    @Test
    public void testParseCompactionDelta() {
        String json =
            "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"compaction_delta\",\"content\":\"compressed_data_123\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.COMPACTION_DELTA, event.getType());
        assertEquals(2, event.getIndex());
        assertEquals("compressed_data_123", event.getText());
    }

    @Test
    public void testParseCompactionDeltaWithEmptyContent() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"compaction_delta\",\"content\":\"\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.COMPACTION_DELTA, event.getType());
        assertEquals("", event.getText());
    }

    @Test
    public void testParseCompactionDeltaWithMissingContent() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"compaction_delta\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.COMPACTION_DELTA, event.getType());
        assertEquals("", event.getText()); // Should default to empty string
    }

    @Test
    public void testParseContentBlockStopWithMissingIndex() {
        String json = "{\"type\":\"content_block_stop\"}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_STOP, event.getType());
        assertEquals(0, event.getIndex()); // Should default to 0
    }

    @Test
    public void testParseTextDeltaWithEmptyText() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TEXT_DELTA, event.getType());
        assertEquals("", event.getText());
    }

    @Test
    public void testParseTextDeltaWithMissingText() {
        String json = "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TEXT_DELTA, event.getType());
        assertEquals("", event.getText()); // Should default to empty string
    }

    @Test
    public void testParseToolInputDeltaWithMissingPartialJson() {
        String json = "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.TOOL_INPUT_DELTA, event.getType());
        assertEquals("", event.getToolInputJson()); // Should default to empty string
    }

    @Test
    public void testParseContentBlockStartToolUseWithMissingFields() {
        String json = "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_START_TOOL_USE, event.getType());
        assertEquals("", event.getToolUseId()); // Should default to empty string
        assertEquals("", event.getToolName()); // Should default to empty string
    }

    @Test
    public void testParseNullJson() {
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(null);

        assertNull(event); // Should handle null gracefully
    }

    @Test
    public void testParseContentBlockStartWithHighIndex() {
        String json = "{\"type\":\"content_block_start\",\"index\":999,\"content_block\":{\"type\":\"text\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.CONTENT_BLOCK_START_TEXT, event.getType());
        assertEquals(999, event.getIndex());
    }

    @Test
    public void testParseCompactionDeltaWithJsonContent() {
        String json =
            "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"compaction_delta\",\"content\":\"{\\\"key\\\":\\\"value\\\"}\"}}";
        InvokeModelEventParser.InvokeModelEvent event = parser.parse(json);

        assertNotNull(event);
        assertEquals(InvokeModelEventParser.EventType.COMPACTION_DELTA, event.getType());
        assertEquals("{\"key\":\"value\"}", event.getText());
    }
}
