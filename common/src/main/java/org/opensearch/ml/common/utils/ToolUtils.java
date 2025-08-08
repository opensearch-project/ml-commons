/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

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

    /**
     * Extracts required parameters based on tool attributes specification.
     * <p>
     * The method performs the following:
     * <ul>
     *     <li>If required parameters are specified in attributes, only those parameters are extracted</li>
     *     <li>If no required parameters are specified, all parameters are returned</li>
     * </ul>
     *
     * @param parameters The input parameters map to extract from
     * @param attributes The attributes map containing required parameter specifications
     * @return Map containing only the required parameters
     */
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

    /**
     * Extracts and processes input parameters, including handling "input" parameter.
     * <p>
     * The method performs the following steps:
     * <ol>
     *     <li>Extracts required parameters based on tool attributes specification</li>
     *     <li>If an "input" parameter exists:
     *         <ul>
     *             <li>Substitutes any parameter placeholders</li>
     *             <li>Parses it as a JSON map</li>
     *             <li>Merges the parsed values with other parameters</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param parameters The raw input parameters
     * @param attributes The tool attributes containing parameter specifications
     * @return Map of processed input parameters
     * @throws IllegalArgumentException if input JSON parsing fails
     */
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

    /**
     * Builds the final parameter map for tool execution.
     * <p>
     * The method performs the following steps:
     * <ol>
     *     <li>Combines tool specification parameters with input parameters</li>
     *     <li>Processes tool-specific parameter prefixes</li>
     *     <li>Applies configuration overrides from tool specification</li>
     *     <li>Adds tenant identification</li>
     * </ol>
     *
     * @param parameters The input parameters to process
     * @param toolSpec The tool specification containing default parameters and configuration
     * @param tenantId The identifier for the tenant
     * @return Map of processed parameters ready for tool execution
     */
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

    /**
     * Filters tool output based on specified output filters in tool parameters.
     * Uses JSONPath expressions to extract specific portions of the response.
     *
     * @param toolParams The tool parameters containing output filter specifications
     * @param response The raw tool response to filter
     * @return Filtered output if successful, original response if filtering fails
     */
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

    /**
     * Parses different types of tool responses into a JSON string representation.
     * <p>
     * Handles the following special cases:
     * <ul>
     *     <li>ModelTensors - converts to XContent JSON representation</li>
     *     <li>ModelTensor - converts to XContent JSON representation</li>
     *     <li>ModelTensorOutput - converts to XContent JSON representation</li>
     *     <li>Other types - converts to generic JSON string</li>
     * </ul>
     *
     * @param output The tool output object to parse
     * @return JSON string representation of the output
     */
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

    /**
     * Gets the tool name from a tool specification.
     * Returns the specified name if available, otherwise returns the tool type.
     *
     * @param toolSpec The tool specification
     * @return The name of the tool
     */
    public static String getToolName(MLToolSpec toolSpec) {
        return toolSpec.getName() != null ? toolSpec.getName() : toolSpec.getType();
    }

}
