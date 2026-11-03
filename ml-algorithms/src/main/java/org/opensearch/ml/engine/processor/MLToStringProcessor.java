/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

/**
 * Processor that converts input to a JSON string representation.
 * <p>
 * This processor serializes any input object into its JSON string format. It's useful for
 * converting structured data (maps, lists, objects) into string format for storage, logging,
 * or passing to systems that expect string input. Optionally, it can escape JSON special
 * characters for embedding JSON within JSON.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>escape_json</b> (optional): Whether to escape JSON special characters in the output.
 *       Default is false. When true, quotes, backslashes, and control characters are escaped.</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Convert object to JSON string
 * {
 *   "type": "to_string"
 * }
 * Input: {"name": "John", "age": 30}
 * Output: "{\"name\":\"John\",\"age\":30}"
 * 
 * // Convert with JSON escaping (for nested JSON)
 * {
 *   "type": "to_string",
 *   "escape_json": true
 * }
 * Input: {"message": "Hello \"World\""}
 * Output: "{\\\"message\\\":\\\"Hello \\\\\\\"World\\\\\\\"\\\"}"
 * 
 * // Convert array to string
 * {
 *   "type": "to_string"
 * }
 * Input: [1, 2, 3]
 * Output: "[1,2,3]"
 * 
 * // Convert simple value to string
 * {
 *   "type": "to_string"
 * }
 * Input: 42
 * Output: "42"
 * 
 * // Already a string (returns as-is)
 * {
 *   "type": "to_string"
 * }
 * Input: "hello"
 * Output: "hello"
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Converts any input type to its JSON string representation</li>
 *   <li>Objects and maps are serialized as JSON objects</li>
 *   <li>Lists and arrays are serialized as JSON arrays</li>
 *   <li>Primitive values are converted to their string representation</li>
 *   <li>Strings are returned as-is (not double-quoted unless escape_json is true)</li>
 *   <li>When escape_json is true, escapes quotes, backslashes, newlines, tabs, etc.</li>
 *   <li>Useful for preparing data for text-based storage or LLM prompts</li>
 * </ul>
 */
@Processor(MLProcessorType.TO_STRING)
public class MLToStringProcessor extends AbstractMLProcessor {

    private final boolean escapeJson;

    public MLToStringProcessor(Map<String, Object> config) {
        super(config);
        this.escapeJson = Boolean.TRUE.equals(config.getOrDefault("escape_json", false));
    }

    @Override
    public Object process(Object input) {
        String text = StringUtils.toJson(input);
        if (escapeJson) {
            return StringEscapeUtils.escapeJson(text);
        }
        return text;
    }
}
