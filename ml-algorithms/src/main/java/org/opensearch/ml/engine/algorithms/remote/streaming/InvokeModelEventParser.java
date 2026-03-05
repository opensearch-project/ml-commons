/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

/**
 * Parser interface for model-specific InvokeModel streaming events.
 * Each model family (Claude, Llama, Mistral, etc.) implements its own parser
 * to convert raw JSON events into a common {@link InvokeModelEvent} format.
 */
public interface InvokeModelEventParser {

    /**
     * Parse a JSON streaming event into a structured event.
     *
     * @param jsonEvent the raw JSON event string from the InvokeModel response stream
     * @return the parsed event, or null if the event type is unknown/ignorable
     */
    InvokeModelEvent parse(String jsonEvent);

    /**
     * Event types emitted by InvokeModel streaming responses.
     */
    enum EventType {
        MESSAGE_START,
        CONTENT_BLOCK_START_TEXT,
        CONTENT_BLOCK_START_TOOL_USE,
        CONTENT_BLOCK_START_COMPACTION,
        TEXT_DELTA,
        TOOL_INPUT_DELTA,
        COMPACTION_DELTA,
        CONTENT_BLOCK_STOP,
        MESSAGE_DELTA,
        MESSAGE_STOP
    }

    /**
     * A parsed streaming event from an InvokeModel response.
     */
    class InvokeModelEvent {
        private final EventType type;
        private final String text;
        private final String toolName;
        private final String toolUseId;
        private final String toolInputJson;
        private final String stopReason;
        private final int index;

        public InvokeModelEvent(
            EventType type,
            String text,
            String toolName,
            String toolUseId,
            String toolInputJson,
            String stopReason,
            int index
        ) {
            this.type = type;
            this.text = text;
            this.toolName = toolName;
            this.toolUseId = toolUseId;
            this.toolInputJson = toolInputJson;
            this.stopReason = stopReason;
            this.index = index;
        }

        public EventType getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getToolName() {
            return toolName;
        }

        public String getToolUseId() {
            return toolUseId;
        }

        public String getToolInputJson() {
            return toolInputJson;
        }

        public String getStopReason() {
            return stopReason;
        }

        public int getIndex() {
            return index;
        }

        public static InvokeModelEvent messageStart() {
            return new InvokeModelEvent(EventType.MESSAGE_START, null, null, null, null, null, 0);
        }

        public static InvokeModelEvent contentBlockStartText(int index) {
            return new InvokeModelEvent(EventType.CONTENT_BLOCK_START_TEXT, null, null, null, null, null, index);
        }

        public static InvokeModelEvent contentBlockStartToolUse(int index, String toolUseId, String toolName) {
            return new InvokeModelEvent(EventType.CONTENT_BLOCK_START_TOOL_USE, null, toolName, toolUseId, null, null, index);
        }

        public static InvokeModelEvent contentBlockStartCompaction(int index) {
            return new InvokeModelEvent(EventType.CONTENT_BLOCK_START_COMPACTION, null, null, null, null, null, index);
        }

        public static InvokeModelEvent textDelta(int index, String text) {
            return new InvokeModelEvent(EventType.TEXT_DELTA, text, null, null, null, null, index);
        }

        public static InvokeModelEvent toolInputDelta(int index, String json) {
            return new InvokeModelEvent(EventType.TOOL_INPUT_DELTA, null, null, null, json, null, index);
        }

        public static InvokeModelEvent compactionDelta(int index, String text) {
            return new InvokeModelEvent(EventType.COMPACTION_DELTA, text, null, null, null, null, index);
        }

        public static InvokeModelEvent contentBlockStop(int index) {
            return new InvokeModelEvent(EventType.CONTENT_BLOCK_STOP, null, null, null, null, null, index);
        }

        public static InvokeModelEvent messageDelta(String stopReason) {
            return new InvokeModelEvent(EventType.MESSAGE_DELTA, null, null, null, null, stopReason, 0);
        }

        public static InvokeModelEvent messageStop() {
            return new InvokeModelEvent(EventType.MESSAGE_STOP, null, null, null, null, null, 0);
        }
    }
}
