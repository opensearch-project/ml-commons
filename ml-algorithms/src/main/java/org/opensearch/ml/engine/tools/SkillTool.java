/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

import com.google.gson.Gson;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports loading agent skills on-demand.
 * It implements progressive disclosure: skills are listed in the description,
 * and full instructions are loaded when the LLM requests a specific skill.
 */
@Getter
@Setter
@Log4j2
@ToolAnnotation(SkillTool.TYPE)
public class SkillTool implements Tool {
    public static final String TYPE = "SkillTool";
    public static final String SKILL_ID = "skill_id";
    public static final String INPUT_SCHEMA_FIELD = "input_schema";
    public static final String DEFAULT_DESCRIPTION = "Invoke a skill to load its instructions and capabilities.";
    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
        + "\"properties\":{\"skill_id\":{\"type\":\"string\",\"description\":\"The ID of the skill to load\"}},"
        + "\"required\":[\"skill_id\"]}";

    private static final Gson GSON = new Gson();

    private String name = TYPE;
    private Map<String, Object> attributes;
    private String description;

    private Parser outputParser;

    private Map<String, Map<String, Object>> skillsMap;

    public SkillTool(Map<String, Map<String, Object>> skillsMap) {
        if (skillsMap == null || skillsMap.isEmpty()) {
            throw new IllegalArgumentException("Skills map cannot be null or empty");
        }
        this.skillsMap = skillsMap;
        this.description = buildDescription(skillsMap);

        this.attributes = new HashMap<>();
        attributes.put(INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
    }

    /**
     * Build the tool description that lists all available skills.
     * This implements the progressive disclosure pattern - only names and descriptions are shown.
     */
    public static String buildDescription(Map<String, Map<String, Object>> skillsMap) {
        StringBuilder desc = new StringBuilder(
            "**PRIMARY TOOL - USE THIS FIRST** for any domain-specific questions. This tool loads expert skills that provide specialized knowledge and guidance. Available skills: \\n"
        );
        for (Map.Entry<String, Map<String, Object>> entry : skillsMap.entrySet()) {
            String skillName = entry.getKey();
            String skillDesc = (String) entry.getValue().get("description");
            desc.append("- ").append(skillName).append(": ");
            if (skillDesc != null && !skillDesc.isEmpty()) {
                desc.append(skillDesc);
            }
            desc.append("\\n");
        }
        return desc.toString();
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String skillId = parameters.get(SKILL_ID);

            if (skillId == null || skillId.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("skill_id parameter is required"));
                return;
            }

            if (!skillsMap.containsKey(skillId)) {
                listener.onFailure(new IllegalArgumentException("Skill not found: " + skillId));
                return;
            }

            Map<String, Object> skillData = skillsMap.get(skillId);
            String instructions = (String) skillData.get("instructions");

            if (instructions == null || instructions.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Skill has no instructions: " + skillId));
                return;
            }

            // Format the response to clearly indicate the skill has been loaded
            String response = "SKILL LOADED: " + skillId + "\nInstructions:\n" + instructions;

            log.info("Loaded skill: {} for agent execution", skillId);

            if (outputParser != null) {
                listener.onResponse((T) outputParser.parse(response));
            } else {
                listener.onResponse((T) response);
            }

        } catch (Exception e) {
            log.error("Failed to load skill", e);
            listener.onFailure(e);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }
        String skillId = parameters.get(SKILL_ID);
        return skillId != null && !skillId.trim().isEmpty() && skillsMap.containsKey(skillId);
    }

    /**
     * Factory for creating SkillTool instances.
     */
    public static class Factory implements Tool.Factory<SkillTool> {
        public static final String SKILLS_MAP_PARAM = "_skills_map";

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SkillTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        @Override
        public SkillTool create(Map<String, Object> params) {
            Object skillsMapObj = params.get(SKILLS_MAP_PARAM);

            if (skillsMapObj == null) {
                throw new IllegalArgumentException("Skills map is required to create SkillTool");
            }

            Map<String, Map<String, Object>> skillsMap;

            // The skills map is stored as a JSON string, so we need to deserialize it
            if (skillsMapObj instanceof String) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, Object>> deserializedMap = GSON
                        .fromJson((String) skillsMapObj, new com.google.gson.reflect.TypeToken<Map<String, Map<String, Object>>>() {
                        }.getType());
                    skillsMap = deserializedMap;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to deserialize skills map: " + e.getMessage(), e);
                }
            } else if (skillsMapObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> castMap = (Map<String, Map<String, Object>>) skillsMapObj;
                skillsMap = castMap;
            } else {
                throw new IllegalArgumentException("Skills map must be a String or Map, got: " + skillsMapObj.getClass().getName());
            }

            if (skillsMap == null || skillsMap.isEmpty()) {
                throw new IllegalArgumentException("Skills map cannot be empty");
            }

            return new SkillTool(skillsMap);
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
            return null;
        }
    }
}
