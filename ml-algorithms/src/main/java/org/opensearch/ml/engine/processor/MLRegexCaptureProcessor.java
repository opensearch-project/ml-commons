/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that captures groups from regex pattern matches.
 * <p>
 * This processor applies a regular expression pattern to the input and extracts specified
 * capture groups. It's useful for parsing structured text, extracting specific portions of
 * strings, or transforming text data based on patterns.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>pattern</b> (required): Regular expression pattern with capture groups.
 *       The pattern is compiled with DOTALL flag (. matches newlines).</li>
 *   <li><b>groups</b> (optional): Capture group index or list of indices to extract.
 *       Default is "1" (first capture group). Can be a single number or array format.</li>
 * </ul>
 * <p>
 * <b>Capture Groups:</b>
 * <ul>
 *   <li>Group 0 is the entire match</li>
 *   <li>Group 1, 2, 3... are the capture groups defined by parentheses in the pattern</li>
 *   <li>If a single group is specified, returns a string</li>
 *   <li>If multiple groups are specified, returns a list of strings</li>
 *   <li>Invalid group indices are silently skipped</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Extract email username (first capture group)
 * {
 *   "type": "regex_capture",
 *   "pattern": "([a-zA-Z0-9._%+-]+)@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
 * }
 * Input: "Contact: john.doe@example.com"
 * Output: "john.doe"
 * 
 * // Extract multiple groups
 * {
 *   "type": "regex_capture",
 *   "pattern": "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})",
 *   "groups": "[1,2]"
 * }
 * Input: "Email: john@example.com"
 * Output: ["john", "example.com"]
 * 
 * // Extract date components
 * {
 *   "type": "regex_capture",
 *   "pattern": "(\\d{4})-(\\d{2})-(\\d{2})",
 *   "groups": "[1,2,3]"
 * }
 * Input: "Date: 2024-03-15"
 * Output: ["2024", "03", "15"]
 * 
 * // Extract entire match (group 0)
 * {
 *   "type": "regex_capture",
 *   "pattern": "\\d{3}-\\d{3}-\\d{4}",
 *   "groups": "0"
 * }
 * Input: "Call me at 555-123-4567"
 * Output: "555-123-4567"
 * 
 * // Extract from multiline text (DOTALL enabled)
 * {
 *   "type": "regex_capture",
 *   "pattern": "&lt;code&gt;(.*)&lt;/code&gt;",
 *   "groups": "1"
 * }
 * Input: "&lt;code&gt;line1\\nline2&lt;/code&gt;"
 * Output: "line1\\nline2"
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Converts input to string representation before matching</li>
 *   <li>Uses Pattern.DOTALL flag so '.' matches newlines</li>
 *   <li>Returns the first match found (does not find all matches)</li>
 *   <li>If no match is found, returns the original input unchanged</li>
 *   <li>If a single group is captured, returns a string</li>
 *   <li>If multiple groups are captured, returns a list of strings</li>
 *   <li>Group indices beyond the pattern's group count are skipped</li>
 *   <li>Errors during matching are logged and original input is returned</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.REGEX_CAPTURE)
public class MLRegexCaptureProcessor extends AbstractMLProcessor {

    private final Pattern pattern;
    private final List<Integer> groupIndices;

    public MLRegexCaptureProcessor(Map<String, Object> config) {
        super(config);
        String patternStr = (String) config.get("pattern");
        try {
            this.pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + patternStr, e);
        }
        this.groupIndices = parseGroups(config.getOrDefault("groups", "1"));
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("pattern")) {
            throw new IllegalArgumentException("'pattern' is required for regex_capture processor");
        }
        String patternValue = (String) config.get("pattern");
        if (patternValue == null || patternValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'pattern' cannot be empty for regex_capture processor");
        }
    }

    @Override
    public Object process(Object input) {
        String text = StringUtils.toJson(input);

        try {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                List<String> captures = new ArrayList<>();
                for (Integer idx : groupIndices) {
                    if (idx >= 0 && idx <= matcher.groupCount()) {
                        String captured = matcher.group(idx);
                        captures.add(captured);
                    } else {
                        log.debug("Group index {} is out of range (pattern has {} groups), skipping", idx, matcher.groupCount());
                    }
                }

                if (captures.isEmpty()) {
                    log.debug("No valid capture groups found, returning original input");
                    return input;
                }

                // Return single string if only one capture, otherwise return list
                if (captures.size() == 1) {
                    return captures.get(0);
                }
                return captures;
            }

            log.debug("Pattern did not match input, returning original");
            return input;
        } catch (Exception e) {
            log.warn("Failed to apply regex capture with pattern '{}': {}", pattern.pattern(), e.getMessage());
            return input;
        }
    }

    /**
     * Parses the groups configuration into a list of group indices.
     * Supports both single integer and array format (e.g., "1" or "[1,2,3]").
     * 
     * @param groupsObj The groups configuration value
     * @return List of group indices to capture
     * @throws IllegalArgumentException if the format is invalid
     */
    private List<Integer> parseGroups(Object groupsObj) {
        List<Integer> indices = new ArrayList<>();

        if (groupsObj instanceof List) {
            // Handle List directly (from JSON array)
            List<?> groupsList = (List<?>) groupsObj;
            for (Object item : groupsList) {
                try {
                    indices.add(Integer.parseInt(item.toString().trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid group index in list: " + item, e);
                }
            }
        } else {
            // Handle string format (single number or "[1,2,3]" format)
            try {
                String groupsStr = groupsObj.toString().trim();
                if (groupsStr.startsWith("[") && groupsStr.endsWith("]")) {
                    // Parse array format: "[1,2,3]"
                    String content = groupsStr.substring(1, groupsStr.length() - 1).trim();
                    if (!content.isEmpty()) {
                        String[] parts = content.split(",");
                        for (String part : parts) {
                            indices.add(Integer.parseInt(part.trim()));
                        }
                    }
                } else {
                    // Parse single number: "1"
                    indices.add(Integer.parseInt(groupsStr));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid 'groups' format: " + groupsObj + ". Expected a number or array like '[1,2,3]'",
                    e
                );
            }
        }

        if (indices.isEmpty()) {
            throw new IllegalArgumentException("'groups' must contain at least one group index");
        }

        return indices;
    }
}
