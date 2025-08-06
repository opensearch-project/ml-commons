/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.StringUtils;

import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for tool-related operations including parameter extraction,
 * tool creation, and output filtering.
 */
@Log4j2
public class ToolUtils {

    public static final String TOOL_OUTPUT_FILTERS_FIELD = "output_filter";
    public static final String TOOL_REQUIRED_PARAMS = "required_parameters";

    public static Map<String, String> extractRequiredParameters(Map<String, String> parameters, Map<String, ?> attributes) {
        Map<String, String> extractedParameters = new HashMap<>();
        if (parameters == null) {
            return extractedParameters;
        }
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
        return extractedParameters;
    }

    public static Map<String, String> extractInputParameters(Map<String, String> parameters, Map<String, ?> attributes) {
        Map<String, String> extractedParameters = ToolUtils.extractRequiredParameters(parameters, attributes);
        if (extractedParameters.containsKey("input")) {
            try {
                StringSubstitutor stringSubstitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                String input = stringSubstitutor.replace(parameters.get("input"));
                extractedParameters.put("input", input);
                Map<String, String> inputParameters = gson
                    .fromJson(input, TypeToken.getParameterized(Map.class, String.class, String.class).getType());
                extractedParameters.putAll(inputParameters);
            } catch (Exception exception) {
                log.info("fail extract parameters from key 'input' due to" + exception.getMessage());
            }
        }
        return extractedParameters;
    }

    public static Map<String, String> buildToolParameters(Map<String, String> parameters, MLToolSpec toolSpec, String tenantId) {
        Map<String, String> executeParams = new HashMap<>();
        if (toolSpec.getParameters() != null) {
            executeParams.putAll(toolSpec.getParameters());
        }
        executeParams.putAll(parameters);

        for (String key : parameters.keySet()) {
            String toolName = getToolName(toolSpec);
            String toolNamePrefix = toolName + ".";
            String toolDescription = toolName + ".description";
            String toolTypePrefix = toolSpec.getType() + ".";
            if (key.startsWith(toolNamePrefix) && !toolDescription.equals(key)) {
                // Don't remove toolName from key, for example SearchIIndexTool.description should keep as is.
                executeParams.put(key.replace(toolNamePrefix, ""), parameters.get(key));
                executeParams.remove(key);
            } else if (key.startsWith(toolTypePrefix)) {
                executeParams.remove(key);
            }
        }
        // Override all parameters in tool config to tool execution parameters as the config contains the static parameters.
        if (toolSpec.getConfigMap() != null && !toolSpec.getConfigMap().isEmpty()) {
            executeParams.putAll(toolSpec.getConfigMap());
        }
        // Place tenant_id last to prevent unintended overriding of the tenant identifier
        executeParams.put(TENANT_ID_FIELD, tenantId);
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

    public static Object filterToolOutput(Map<String, String> toolParams, Object response) {
        if (toolParams != null && toolParams.containsKey(TOOL_OUTPUT_FILTERS_FIELD)) {
            try {
                String output = parseResponse(response);
                Object filteredOutput = JsonPath.read(output, toolParams.get(TOOL_OUTPUT_FILTERS_FIELD));
                return StringUtils.toJson(filteredOutput);
            } catch (PathNotFoundException e) {
                log.error("JSONPath not found: [{}]", toolParams.get(TOOL_OUTPUT_FILTERS_FIELD), e);
            } catch (Exception e) {
                // TODO: another option is returning error if failed to parse, need test to check which option is better.
                log.error("Failed to read tool response from path [{}]", toolParams.get(TOOL_OUTPUT_FILTERS_FIELD), e);
            }
        }
        return response;
    }

    public static String parseResponse(Object output) {
        try {
            if (output instanceof List && !((List) output).isEmpty() && ((List) output).get(0) instanceof ModelTensors) {
                ModelTensors tensors = (ModelTensors) ((List) output).get(0);
                return tensors.toXContent(JsonXContent.contentBuilder(), null).toString();
            } else if (output instanceof ModelTensor) {
                return ((ModelTensor) output).toXContent(JsonXContent.contentBuilder(), null).toString();
            } else if (output instanceof ModelTensorOutput) {
                return ((ModelTensorOutput) output).toXContent(JsonXContent.contentBuilder(), null).toString();
            } else {
                return StringUtils.toJson(output);
            }
        } catch (Exception e) {
            return StringUtils.toJson(output);
        }
    }

    public static List<String> getToolNames(Map<String, Tool> tools) {
        final List<String> inputTools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = entry.getValue().getName();
            inputTools.add(toolName);
        }
        return inputTools;
    }

    public static String getToolName(MLToolSpec toolSpec) {
        return toolSpec.getName() != null ? toolSpec.getName() : toolSpec.getType();
    }
}
