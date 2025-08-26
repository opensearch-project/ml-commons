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

@ToolAnnotation(value = "ReadFromScratchPadTool")
public class ReadFromScratchPadTool implements Tool {
    public static final String TYPE = "ReadFromScratchPadTool";
    public static final String STRICT_FIELD = "strict";
    public static final String NOTES_KEY = "notes";
    public static final String SCRATCHPAD_NOTES_KEY = "_scratchpad_notes";
    public static final String PERSISTENT_NOTES_KEY = "persistent_notes";
    private static final String DEFAULT_DESCRIPTION =
        "Retrieve previous research work and notes from the persistent scratchpad for the current conversation.";

    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private String name = TYPE;
    private String description = DEFAULT_DESCRIPTION;

    private Map<String, Object> attributes;

    public ReadFromScratchPadTool() {
        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, false);
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
        return true;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        // The agent runner will intercept this call and substitute this placeholder
        // with the actual content from the persistent scratchpad.
        String existing_notes = StringUtils.toJson(parameters.getOrDefault(SCRATCHPAD_NOTES_KEY, ""));
        String persistent_notes = parameters.getOrDefault(PERSISTENT_NOTES_KEY, "");
        if (persistent_notes != null && !persistent_notes.isEmpty()) {
            if (!existing_notes.contains(persistent_notes)) {
                existing_notes += "\n" + persistent_notes;
            }
        }
        parameters.put(SCRATCHPAD_NOTES_KEY, existing_notes);
        listener.onResponse((T) ("Notes from scratchpad: " + existing_notes));
    }

    public static class Factory implements Tool.Factory<ReadFromScratchPadTool> {
        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new Factory();
            }
            return INSTANCE;
        }

        public void init() {}

        @Override
        public ReadFromScratchPadTool create(Map<String, Object> params) {
            return new ReadFromScratchPadTool();
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
