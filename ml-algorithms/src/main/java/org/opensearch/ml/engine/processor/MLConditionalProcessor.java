/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;
import net.minidev.json.JSONArray;

/**
 * Processor that applies different processing chains based on conditional routing.
 * <p>
 * This processor evaluates a value (either the entire input or a field extracted via JsonPath)
 * against multiple conditions and applies the corresponding processor chain for the first matching
 * condition. If no conditions match, an optional default processor chain is applied.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>path</b> (optional): JsonPath expression to extract the value to evaluate.
 *       If omitted, the entire input is evaluated.</li>
 *   <li><b>routes</b> (required): A list of condition-to-processors mappings.
 *       Each route specifies a condition and the processors to apply if that condition matches.</li>
 *   <li><b>default</b> (optional): Processor chain to apply if no conditions match.
 *       If omitted, the input is returned unchanged when no conditions match.</li>
 * </ul>
 * <p>
 * <b>Supported Conditions:</b>
 * <ul>
 *   <li><b>Exact match:</b> "value" - Matches if the value equals the string exactly</li>
 *   <li><b>Existence checks:</b>
 *     <ul>
 *       <li>"exists" - Matches if the value exists and is not null</li>
 *       <li>"null" or "not_exists" - Matches if the value is null or doesn't exist</li>
 *     </ul>
 *   </li>
 *   <li><b>Numeric comparisons:</b> (works with numbers or numeric strings)
 *     <ul>
 *       <li>"&gt;10" - Greater than</li>
 *       <li>"&lt;10" - Less than</li>
 *       <li>"&gt;=10" - Greater than or equal to</li>
 *       <li>"&lt;=10" - Less than or equal to</li>
 *       <li>"==10" - Equal to (numeric comparison)</li>
 *     </ul>
 *   </li>
 *   <li><b>String operations:</b>
 *     <ul>
 *       <li>"regex:pattern" - Matches if the value matches the regex pattern</li>
 *       <li>"contains:substring" - Matches if the value contains the substring</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Route based on a field value
 * {
 *   "type": "conditional",
 *   "path": "$.user.role",
 *   "routes": [
 *     {
 *       "admin": [
 *         {"type": "keep_fields", "fields": ["username", "email", "permissions"]}
 *       ]
 *     },
 *     {
 *       "user": [
 *         {"type": "keep_fields", "fields": ["username", "email"]}
 *       ]
 *     }
 *   ],
 *   "default": [
 *     {"type": "keep_fields", "fields": ["username"]}
 *   ]
 * }
 * 
 * // Route based on numeric comparison
 * {
 *   "type": "conditional",
 *   "path": "$.user.age",
 *   "routes": [
 *     {
 *       "&gt;=18": [
 *         {"type": "process_and_set", "path": "$.category", "processors": [{"type": "to_string"}]}
 *       ]
 *     },
 *     {
 *       "&lt;18": [
 *         {"type": "remove_fields", "fields": ["sensitiveData"]}
 *       ]
 *     }
 *   ]
 * }
 * 
 * // Route based on regex pattern
 * {
 *   "type": "conditional",
 *   "path": "$.email",
 *   "routes": [
 *     {
 *       "regex:.*@company\\.com$": [
 *         {"type": "process_and_set", "path": "$.isInternal", "processors": [{"type": "to_string"}]}
 *       ]
 *     }
 *   ],
 *   "default": [
 *     {"type": "process_and_set", "path": "$.isInternal", "processors": [{"type": "to_string"}]}
 *   ]
 * }
 * 
 * // Route based on existence
 * {
 *   "type": "conditional",
 *   "path": "$.optional.field",
 *   "routes": [
 *     {
 *       "exists": [
 *         {"type": "to_string"}
 *       ]
 *     },
 *     {
 *       "not_exists": [
 *         {"type": "process_and_set", "path": "$.optional.field", "processors": [{"type": "to_string"}]}
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Routes are evaluated in order; the first matching condition is applied</li>
 *   <li>If no path is specified, the entire input is evaluated against conditions</li>
 *   <li>If the path doesn't exist, the value is treated as null</li>
 *   <li>If no conditions match and no default is specified, the input is returned unchanged</li>
 *   <li>Numeric comparisons work with both Number types and numeric strings</li>
 *   <li>Errors during path evaluation are logged and treated as null values</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.CONDITIONAL)
public class MLConditionalProcessor extends AbstractMLProcessor {

    private final String path;
    private final List<Map.Entry<String, List<MLProcessor>>> conditionalProcessors;
    private final List<MLProcessor> defaultProcessors;

    @SuppressWarnings("unchecked")
    public MLConditionalProcessor(Map<String, Object> config) {
        super(config);
        this.path = (String) config.get("path");

        // Parse routes
        Object routesObj = config.get("routes");
        if (routesObj == null) {
            throw new IllegalArgumentException("'routes' is required for conditional processor");
        }

        List<Object> routesList = (List<Object>) routesObj;
        this.conditionalProcessors = new ArrayList<>();

        for (Object routeObj : routesList) {
            if (routeObj instanceof Map) {
                Map<String, Object> routeMap = (Map<String, Object>) routeObj;
                for (Map.Entry<String, Object> routeEntry : routeMap.entrySet()) {
                    List<MLProcessor> processors = ProcessorChain.parseProcessorConfigs(routeEntry.getValue());
                    conditionalProcessors.add(new AbstractMap.SimpleEntry<>(routeEntry.getKey(), processors));
                }
            }
        }

        if (conditionalProcessors.isEmpty()) {
            throw new IllegalArgumentException("'routes' must contain at least one route for conditional processor");
        }

        // Parse default
        this.defaultProcessors = config.containsKey("default")
            ? ProcessorChain.parseProcessorConfigs(config.get("default"))
            : Collections.emptyList();
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("routes")) {
            throw new IllegalArgumentException("'routes' is required for conditional processor");
        }
    }

    @Override
    public Object process(Object input) {
        Object valueToCheck = input;

        if (path != null && !path.isEmpty()) {
            try {
                String jsonStr = StringUtils.toJson(input);
                try {
                    valueToCheck = JsonPath.read(jsonStr, path);
                } catch (PathNotFoundException e) {
                    valueToCheck = null;
                }
            } catch (Exception e) {
                log.warn("Error evaluating path {}: {}", path, e.getMessage());
            }
        }

        for (Map.Entry<String, List<MLProcessor>> entry : conditionalProcessors) {
            String condition = entry.getKey();
            if (matchesCondition(condition, valueToCheck)) {
                return ProcessorChain.applyProcessors(input, entry.getValue());
            }
        }

        return ProcessorChain.applyProcessors(input, defaultProcessors);
    }

    private boolean matchesCondition(String condition, Object value) {
        // Handle null or non-existent values
        if (value == null) {
            return "null".equals(condition) || "not_exists".equals(condition);
        }

        // Handle empty arrays as null (optional behavior)
        if (value instanceof JSONArray && ((JSONArray) value).isEmpty()) {
            return "null".equals(condition) || "not_exists".equals(condition);
        }

        // Check existence
        if ("exists".equals(condition)) {
            return true;
        }

        // Exact string match
        String strValue = value.toString();
        if (condition.equals(strValue)) {
            return true;
        }

        // Numeric comparisons
        if (value instanceof Number || canParseAsNumber(strValue)) {
            try {
                double numValue = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(strValue);

                if (condition.startsWith(">=")) {
                    return numValue >= Double.parseDouble(condition.substring(2));
                }
                if (condition.startsWith("<=")) {
                    return numValue <= Double.parseDouble(condition.substring(2));
                }
                if (condition.startsWith(">")) {
                    return numValue > Double.parseDouble(condition.substring(1));
                }
                if (condition.startsWith("<")) {
                    return numValue < Double.parseDouble(condition.substring(1));
                }
                if (condition.startsWith("==")) {
                    return Math.abs(numValue - Double.parseDouble(condition.substring(2))) < 1e-10;
                }
            } catch (NumberFormatException e) {
                log.debug("Invalid numeric condition '{}': {}", condition, e.getMessage());
                return false;
            }
        }

        // Regex matching
        if (condition.startsWith("regex:")) {
            try {
                return Pattern.matches(condition.substring(6), strValue);
            } catch (Exception e) {
                log.debug("Invalid regex pattern '{}': {}", condition, e.getMessage());
                return false;
            }
        }

        // Substring matching
        if (condition.startsWith("contains:")) {
            return strValue.contains(condition.substring(9));
        }

        return false;
    }

    private boolean canParseAsNumber(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
