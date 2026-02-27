/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * Parses Claude Messages API streaming events into {@link InvokeModelEvent} objects.
 *
 * Claude streaming events follow the format:
 * <pre>
 * {"type":"message_start","message":{...}}
 * {"type":"content_block_start","index":0,"content_block":{"type":"text",...}}
 * {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 * {"type":"content_block_stop","index":0}
 * {"type":"message_delta","delta":{"stop_reason":"end_turn",...}}
 * {"type":"message_stop"}
 * </pre>
 */
@Log4j2
public class ClaudeInvokeModelEventParser implements InvokeModelEventParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public InvokeModelEvent parse(String jsonEvent) {
        try {
            JsonNode root = MAPPER.readTree(jsonEvent);
            String type = root.path("type").asText("");

            switch (type) {
                case "message_start":
                    return InvokeModelEvent.messageStart();

                case "content_block_start":
                    return parseContentBlockStart(root);

                case "content_block_delta":
                    return parseContentBlockDelta(root);

                case "content_block_stop":
                    return InvokeModelEvent.contentBlockStop(root.path("index").asInt(0));

                case "message_delta":
                    return parseMessageDelta(root);

                case "message_stop":
                    return InvokeModelEvent.messageStop();

                default:
                    log.debug("Unknown Claude event type: {}", type);
                    return null;
            }
        } catch (Exception e) {
            log.warn("Failed to parse Claude streaming event: {}", jsonEvent, e);
            return null;
        }
    }

    private InvokeModelEvent parseContentBlockStart(JsonNode root) {
        int index = root.path("index").asInt(0);
        JsonNode contentBlock = root.path("content_block");
        String blockType = contentBlock.path("type").asText("");

        switch (blockType) {
            case "text":
                return InvokeModelEvent.contentBlockStartText(index);

            case "tool_use":
                String toolUseId = contentBlock.path("id").asText("");
                String toolName = contentBlock.path("name").asText("");
                return InvokeModelEvent.contentBlockStartToolUse(index, toolUseId, toolName);

            case "compaction":
                return InvokeModelEvent.contentBlockStartCompaction(index);

            default:
                log.debug("Unknown content block type: {}", blockType);
                return null;
        }
    }

    private InvokeModelEvent parseContentBlockDelta(JsonNode root) {
        int index = root.path("index").asInt(0);
        JsonNode delta = root.path("delta");
        String deltaType = delta.path("type").asText("");

        switch (deltaType) {
            case "text_delta":
                return InvokeModelEvent.textDelta(index, delta.path("text").asText(""));

            case "input_json_delta":
                return InvokeModelEvent.toolInputDelta(index, delta.path("partial_json").asText(""));

            case "compaction_delta":
                return InvokeModelEvent.compactionDelta(index, delta.path("summary").asText(""));

            default:
                log.debug("Unknown delta type: {}", deltaType);
                return null;
        }
    }

    private InvokeModelEvent parseMessageDelta(JsonNode root) {
        JsonNode delta = root.path("delta");
        String stopReason = delta.path("stop_reason").asText(null);
        return InvokeModelEvent.messageDelta(stopReason);
    }
}
