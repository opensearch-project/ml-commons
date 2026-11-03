package org.opensearch.ml.engine.processor;

/**
 * Interface for ML data processors that transform, filter, or manipulate data.
 * <p>
 * Processors are composable units that can be chained together to perform complex
 * data transformations. Each processor takes an input object, performs a specific
 * operation, and returns the processed result. Processors are commonly used for:
 * <ul>
 *   <li>Data transformation (format conversion, serialization)</li>
 *   <li>Data extraction (JsonPath filtering, regex capture)</li>
 *   <li>Data manipulation (field operations, text replacement)</li>
 *   <li>Control flow (conditional routing, nested processing)</li>
 * </ul>
 * <p>
 * <b>Available Processor Types:</b>
 * <ul>
 *   <li><b>to_string</b> - Convert input to JSON string representation</li>
 *   <li><b>regex_replace</b> - Find and replace using regex patterns</li>
 *   <li><b>regex_capture</b> - Extract capture groups from regex matches</li>
 *   <li><b>jsonpath_filter</b> - Extract data using JsonPath expressions</li>
 *   <li><b>extract_json</b> - Parse and extract JSON from text</li>
 *   <li><b>keep_fields</b> - Keep only specified fields (whitelist)</li>
 *   <li><b>remove_fields</b> - Remove specified fields (blacklist)</li>
 *   <li><b>set_field</b> - Set a field to a static value</li>
 *   <li><b>rename_fields</b> - Rename fields for normalization</li>
 *   <li><b>conditional</b> - Route to different processor chains based on conditions</li>
 *   <li><b>process_and_set</b> - Apply processor chain and set result at a path</li>
 * </ul>
 * <p>
 * <b>Processor Chain Example:</b>
 * <pre>
 * {
 *   "processors": [
 *     {
 *       "type": "extract_json",
 *       "extract_type": "object"
 *     },
 *     {
 *       "type": "keep_fields",
 *       "fields": ["username", "email", "profile"]
 *     },
 *     {
 *       "type": "set_field",
 *       "path": "$.processed",
 *       "value": true
 *     },
 *     {
 *       "type": "to_string"
 *     }
 *   ]
 * }
 * </pre>
 * <p>
 * <b>Implementation Guidelines:</b>
 * <ul>
 *   <li>Processors should be stateless and thread-safe</li>
 *   <li>Return the original input unchanged on errors (fail gracefully)</li>
 *   <li>Log errors at appropriate levels (debug for expected, warn for unexpected)</li>
 *   <li>Validate configuration in the constructor</li>
 *   <li>Support both simple field names and JsonPath expressions where applicable</li>
 *   <li>Document behavior with comprehensive JavaDoc and examples</li>
 * </ul>
 * <p>
 * <b>Error Handling:</b>
 * Processors should handle errors gracefully and return the original input when
 * processing fails. This ensures that a single processor failure doesn't break
 * the entire chain. Errors should be logged with sufficient context for debugging.
 * 
 * @see AbstractMLProcessor Base class for implementing processors
 * @see ProcessorChain Utility for chaining processors together
 * @see MLProcessorType Enum of available processor types
 */
public interface MLProcessor {
    /**
     * Processes the input object and returns the transformed result.
     * <p>
     * Implementations should:
     * <ul>
     *   <li>Accept any input type and handle it appropriately</li>
     *   <li>Return the processed result (can be a different type than input)</li>
     *   <li>Return the original input unchanged if processing fails</li>
     *   <li>Not throw exceptions (catch and log instead)</li>
     *   <li>Be thread-safe and stateless</li>
     * </ul>
     * 
     * @param input The object to process. Can be any type: String, Map, List, primitive, etc.
     * @return The processed result. Type depends on the processor implementation.
     *         Returns the original input unchanged if processing fails.
     */
    Object process(Object input);
}
