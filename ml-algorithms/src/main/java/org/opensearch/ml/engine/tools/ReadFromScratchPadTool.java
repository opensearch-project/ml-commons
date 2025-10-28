/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.utils.StringUtils;

@ToolAnnotation(value = "ReadFromScratchPadTool")
public class ReadFromScratchPadTool implements Tool {
    public static final String TYPE = "ReadFromScratchPadTool";
    public static final String STRICT_FIELD = "strict";
    public static final String SCRATCHPAD_NOTES_KEY = "_scratchpad_notes";
    public static final String NOTES_KEY = "notes";
    public static final String PERSISTENT_NOTES_KEY = "persistent_notes";
    private static final String DEFAULT_DESCRIPTION = "Retrieve previous research work and notes from the persistent scratchpad.";

    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private String name = TYPE;
    private String description = DEFAULT_DESCRIPTION;

    private Map<String, Object> attributes;

    public ReadFromScratchPadTool() {
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
        return true;
    }

    @Override
    public boolean validateParameterTypes(Map<String, Object> parameters) {
        // Validate persistent_notes must be String
        Object persistentNotesObj = parameters.get(PERSISTENT_NOTES_KEY);
        if (persistentNotesObj != null && !(persistentNotesObj instanceof String)) {
            throw new IllegalArgumentException(
                String.format("%s must be a String type, but got %s", PERSISTENT_NOTES_KEY, persistentNotesObj.getClass().getSimpleName())
            );
        }
        return true;
    }

    /**
     * Executes the ReadFromScratchPadTool.
     * This tool retrieves notes from the persistent scratchpad for the current conversation.
     * The scratchpad notes are stored as a List<String> in the parameters map under
     * the SCRATCHPAD_NOTES_KEY. The tool handles both existing List<String> format
     * and legacy JSON string format for backward compatibility. It also supports
     * adding persistent notes that are not already in the scratchpad.
     *
     * @param parameters A map containing tool-specific parameters. Expected to contain:
     *                   - {@code SCRATCHPAD_NOTES_KEY} (optional): Existing notes as List<String> or JSON string
     *                   - {@code PERSISTENT_NOTES_KEY} (optional): Additional note to add if not already present
     * @param listener The action listener to report the result of the tool execution.
     *                 On success, returns formatted notes from the scratchpad or "Scratchpad is empty."
     */
    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {

        List<String> notes;
        String existingNotes = parameters.getOrDefault(SCRATCHPAD_NOTES_KEY, "[]");

        List<String> parsedNotes = StringUtils.parseStringArrayToList(existingNotes);
        notes = parsedNotes != null ? new ArrayList<>(parsedNotes) : new ArrayList<>();

        String persistentNotes = parameters.getOrDefault(PERSISTENT_NOTES_KEY, "");

        if (persistentNotes != null && !persistentNotes.isEmpty() && !notes.contains(persistentNotes)) {
            notes.add(persistentNotes);
        }

        parameters.put(SCRATCHPAD_NOTES_KEY, StringUtils.toJson(notes));

        if (notes.isEmpty()) {
            listener.onResponse((T) "Scratchpad is empty.");
        } else {
            String formattedNotes = "- " + String.join("\n- ", notes);
            listener.onResponse((T) ("Notes from scratchpad:\n" + formattedNotes));
        }
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
            return Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, true);
        }
    }
}
