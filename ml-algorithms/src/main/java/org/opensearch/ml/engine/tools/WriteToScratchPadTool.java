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

@ToolAnnotation(value = "WriteToScratchPadTool")
public class WriteToScratchPadTool implements Tool {
    public static final String TYPE = "WriteToScratchPadTool";
    public static final String SCRATCHPAD_NOTES_KEY = "_scratchpad_notes";
    public static final String NOTES_KEY = "notes";
    public static final String INCLUDE_HISTORY_KEY = "include_history";
    public static final String STRICT_FIELD = "strict";
    private static final String DEFAULT_DESCRIPTION = "Save research plans, findings, and progress updates to a persistent scratchpad.";

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

    /**
     * Executes the WriteToScratchPadTool.
     * This tool saves notes to the persistent scratchpad for the current conversation.
     * The scratchpad notes are stored as a List<String> in the parameters map under
     * the SCRATCHPAD_NOTES_KEY. The tool handles both existing List<String> format
     * and legacy JSON string format for backward compatibility.
     *
     * @param parameters A map containing tool-specific parameters. Expected to contain:
     *                   - {@code NOTES_KEY} (required): The note to be saved
     *                   - {@code INCLUDE_HISTORY_KEY} (optional): Boolean string to include full history in response
     *                   - {@code SCRATCHPAD_NOTES_KEY} (optional): Existing notes as List<String> or JSON string
     * @param listener The action listener to report the result of the tool execution.
     *                 On success, returns either a simple confirmation or full scratchpad content.
     */
    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        String currentNote = parameters.get(NOTES_KEY);

        final boolean includeHistory = parameters.containsKey(INCLUDE_HISTORY_KEY)
            && Boolean.parseBoolean(parameters.get(INCLUDE_HISTORY_KEY));

        if (currentNote == null || currentNote.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Parameter 'notes' is required for WriteToScratchPadTool."));
            return;
        }

        // Handle both List<String> and String (JSON) formats for existing notes
        List<String> notes;
        Map rawParameters = parameters;
        Object existingNotes = rawParameters.get(SCRATCHPAD_NOTES_KEY);
        if (existingNotes instanceof List) {
            notes = new ArrayList<>((List<String>) existingNotes);
        } else if (existingNotes instanceof String) {
            List<String> parsedNotes = StringUtils.parseStringArrayToList((String) existingNotes);
            notes = parsedNotes != null ? new ArrayList<>(parsedNotes) : new ArrayList<>();
        } else {
            notes = new ArrayList<>();
        }

        notes.add(currentNote);
        rawParameters.put(SCRATCHPAD_NOTES_KEY, notes);

        if (includeHistory) {
            String fullNotesFormatted = "- " + String.join("\n- ", notes);
            listener.onResponse((T) ("Scratchpad updated. Full content:\n" + fullNotesFormatted));
        } else {
            listener.onResponse((T) ("Wrote to scratchpad: " + currentNote));
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
            return Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, true);
        }
    }
}
