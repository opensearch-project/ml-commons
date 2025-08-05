/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ToolUtils {

    public static final String TOOL_OUTPUT_FILTERS_FIELD = "output_filter";
    public static final String TOOL_REQUIRED_PARAMS = "required_parameters";

    public static Map<String, String> extractRequiredParameters(Map<String, String> parameters, Map<String, ?> attributes) {
        Map<String, String> extractedParameters = new HashMap<>();
        if (attributes != null && attributes.containsKey(TOOL_REQUIRED_PARAMS)) {
            List<String> requiredParameters = StringUtils.parseStringArrayToList((String) attributes.get(TOOL_REQUIRED_PARAMS));
            if (requiredParameters != null) {
                for (String requiredParameter : requiredParameters) {
                    extractedParameters.put(requiredParameter, parameters.get(requiredParameter));
                }
            }
        } else {
            extractedParameters.putAll(parameters);
        }
        if (parameters.containsKey("input")) {
            String input = parameters.get("input");
            StringSubstitutor stringSubstitutor = new StringSubstitutor(extractedParameters, "${parameters.", "}");
            input = stringSubstitutor.replace(input);
            extractedParameters.put("input", input);
        }
        return extractedParameters;
    }

    public static Map<String, String> buildToolParameters(Map<String, String> parameters, MLToolSpec toolSpec, String tenantId) {
        Map<String, String> executeParams = extractRequiredParameters(parameters, toolSpec.getAttributes());
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        executeParams.put(TENANT_ID_FIELD, tenantId);
        for (String key : parameters.keySet()) {
            String toolNamePrefix = getToolName(toolSpec) + ".";
            if (key.startsWith(toolNamePrefix)) {
                executeParams.put(key.replace(toolNamePrefix, ""), parameters.get(key));
            }
        }
        return executeParams;
    }

    public static Tool createTool(Map<String, Tool.Factory> toolFactories, Map<String, String> executeParams, MLToolSpec toolSpec) {
        if (!toolFactories.containsKey(toolSpec.getType())) {
            throw new IllegalArgumentException("Tool not found: " + toolSpec.getType());
        }
        Map<String, Object> toolParams = new HashMap<>();
        toolParams.putAll(executeParams);
        Map<String, Object> runtimeResources = toolSpec.getRuntimeResources();
        if (runtimeResources != null) {
            toolParams.putAll(runtimeResources);
        }
        Tool tool = toolFactories.get(toolSpec.getType()).create(toolParams);
        String toolName = getToolName(toolSpec);
        tool.setName(toolName);

        if (toolSpec.getDescription() != null) {
            tool.setDescription(toolSpec.getDescription());
        }
        if (executeParams.containsKey(toolName + ".description")) {
            tool.setDescription(executeParams.get(toolName + ".description"));
        }

        return tool;
    }

    public static String filterToolOutput(Map<String, String> toolParams, Object originalOutput) {
        String output = StringUtils.toJson(originalOutput);
        if (toolParams != null && toolParams.containsKey(TOOL_OUTPUT_FILTERS_FIELD)) {
            try {
                Object filteredOutput = JsonPath.read(output, toolParams.get(TOOL_OUTPUT_FILTERS_FIELD));
                output = StringUtils.toJson(filteredOutput);
            } catch (Exception e) {
                log.error("Failed to read tool response from path [{}]", toolParams.get(TOOL_OUTPUT_FILTERS_FIELD), e);
            }
        }
        return output;
    }

}
