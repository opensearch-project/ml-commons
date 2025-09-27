/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.utils.StringUtils;

@ToolAnnotation(value = "WriteToScratchPadTool")
public class WriteToScratchPadTool implements Tool {
    public static final String TYPE = "WriteToScratchPadTool";
    public static final String SCRATCHPAD_NOTES_KEY = "_scratchpad_notes";
    public static final String NOTES_KEY = "notes";
    public static final String INCLUDE_HISTORY_KEY = "include_history";
    public static final String STRICT_FIELD = "strict";
    private static final String DEFAULT_DESCRIPTION =
        "Save research plans, findings, and progress updates to a persistent scratchpad for the current conversation.";

    public static final String DEFAULT_INPUT_SCHEMA =
        "{\"type\":\"object\",\"properties\":{\"notes\":{\"type\":\"string\",\"description\":\"The notes to be saved to the scratchpad.\"}},\"required\":[\"notes\"]}";

    private String name = TYPE;
    private String description = DEFAULT_DESCRIPTION;

    private Map<String, Object> attributes;

    public WriteToScratchPadTool() {
        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, true);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null && parameters.containsKey(NOTES_KEY);
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        // This tool's core logic for state management will be handled by the agent runner,
        // which manages the scratchpad for the entire conversation. This class defines the tool's interface.
        String current_notes = parameters.get(NOTES_KEY);

        final boolean include_history = parameters.containsKey(INCLUDE_HISTORY_KEY)
            && Boolean.parseBoolean(parameters.get(INCLUDE_HISTORY_KEY));

        if (current_notes == null || current_notes.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Parameter 'notes' is required for WriteToScratchPadTool."));
            return;
        }

        String existing_notes = StringUtils.toJson(parameters.getOrDefault(SCRATCHPAD_NOTES_KEY, ""));
        parameters.put(SCRATCHPAD_NOTES_KEY, existing_notes + "\n" + current_notes);
        // The agent runner will intercept this call to update the persistent scratchpad.
        // This response is what the LLM will see as the observation.
        if (include_history) {
            listener.onResponse((T) ("Wrote to scratchpad: " + parameters.get(SCRATCHPAD_NOTES_KEY)));
        } else {
            listener.onResponse((T) ("Wrote to scratchpad: " + current_notes));
        }
    }

    public static class Factory implements Tool.Factory<WriteToScratchPadTool> {
        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new Factory();
            }
            return INSTANCE;
        }

        public void init() {}

        @Override
        public WriteToScratchPadTool create(Map<String, Object> params) {
            return new WriteToScratchPadTool();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return "1";
        }

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);
        }
    }
}
