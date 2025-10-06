/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;
import java.util.regex.Pattern;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.Processor;

import lombok.extern.log4j.Log4j2;

/**
 * Processor that performs regex-based find and replace operations on input text.
 * <p>
 * This processor applies a regular expression pattern to the input and replaces matching
 * text with a specified replacement string. It's useful for text normalization, data
 * sanitization, format transformation, and cleaning unstructured text data.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *   <li><b>pattern</b> (required): Regular expression pattern to match.
 *       The pattern is compiled with DOTALL flag (. matches newlines).</li>
 *   <li><b>replacement</b> (optional): String to replace matches with. Default is "" (empty string).
 *       Supports backreferences like $1, $2 for capture groups.</li>
 *   <li><b>replace_all</b> (optional): Whether to replace all matches or just the first.
 *       Default is true (replace all occurrences).</li>
 * </ul>
 * <p>
 * <b>Replacement Syntax:</b>
 * <ul>
 *   <li><b>Literal text:</b> "replacement" - Replaces with literal string</li>
 *   <li><b>Backreferences:</b> "$1", "$2" - References capture groups from the pattern</li>
 *   <li><b>Escaped dollar:</b> "\\$" - Literal dollar sign in replacement</li>
 *   <li><b>Empty string:</b> "" - Removes matched text</li>
 * </ul>
 * <p>
 * <b>Examples:</b>
 * <pre>
 * // Remove all non-alphanumeric characters
 * {
 *   "type": "regex_replace",
 *   "pattern": "[^a-zA-Z0-9]",
 *   "replacement": ""
 * }
 * Input: "Hello, World! 123"
 * Output: "HelloWorld123"
 * 
 * // Replace spaces with underscores
 * {
 *   "type": "regex_replace",
 *   "pattern": "\\s+",
 *   "replacement": "_"
 * }
 * Input: "hello   world"
 * Output: "hello_world"
 * 
 * // Normalize phone numbers using capture groups
 * {
 *   "type": "regex_replace",
 *   "pattern": "\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})",
 *   "replacement": "$1-$2-$3"
 * }
 * Input: "(555) 123-4567"
 * Output: "555-123-4567"
 * 
 * // Replace only first occurrence
 * {
 *   "type": "regex_replace",
 *   "pattern": "foo",
 *   "replacement": "bar",
 *   "replace_all": false
 * }
 * Input: "foo foo foo"
 * Output: "bar foo foo"
 * 
 * // Redact email addresses
 * {
 *   "type": "regex_replace",
 *   "pattern": "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
 *   "replacement": "[REDACTED]"
 * }
 * Input: "Contact john@example.com or jane@test.org"
 * Output: "Contact [REDACTED] or [REDACTED]"
 * 
 * // Clean up whitespace
 * {
 *   "type": "regex_replace",
 *   "pattern": "\\s+",
 *   "replacement": " "
 * }
 * Input: "text  with   irregular    spacing"
 * Output: "text with irregular spacing"
 * </pre>
 * <p>
 * <b>Behavior:</b>
 * <ul>
 *   <li>Converts input to string representation before processing</li>
 *   <li>Uses Pattern.DOTALL flag so '.' matches newlines</li>
 *   <li>By default, replaces all occurrences of the pattern</li>
 *   <li>Set replace_all to false to replace only the first occurrence</li>
 *   <li>Supports backreferences to capture groups in the replacement string</li>
 *   <li>If no matches are found, returns the input unchanged</li>
 *   <li>If replacement fails, returns the original input and logs a warning</li>
 * </ul>
 */
@Log4j2
@Processor(MLProcessorType.REGEX_REPLACE)
public class MLRegexReplaceProcessor extends AbstractMLProcessor {

    private final Pattern pattern;
    private final String replacement;
    private final boolean replaceAll;

    public MLRegexReplaceProcessor(Map<String, Object> config) {
        super(config);
        String patternStr = (String) config.get("pattern");
        try {
            this.pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + patternStr, e);
        }
        this.replacement = (String) config.getOrDefault("replacement", "");
        this.replaceAll = Boolean.TRUE.equals(config.getOrDefault("replace_all", true));
    }

    @Override
    protected void validateConfig() {
        if (!config.containsKey("pattern")) {
            throw new IllegalArgumentException("'pattern' is required for regex_replace processor");
        }
        String patternValue = (String) config.get("pattern");
        if (patternValue == null || patternValue.trim().isEmpty()) {
            throw new IllegalArgumentException("'pattern' cannot be empty for regex_replace processor");
        }
    }

    @Override
    public Object process(Object input) {
        String text = StringUtils.toJson(input);
        try {
            String result;
            if (replaceAll) {
                result = pattern.matcher(text).replaceAll(replacement);
            } else {
                result = pattern.matcher(text).replaceFirst(replacement);
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to apply regex replacement with pattern '{}': {}", pattern.pattern(), e.getMessage());
            return input;
        }
    }
}
