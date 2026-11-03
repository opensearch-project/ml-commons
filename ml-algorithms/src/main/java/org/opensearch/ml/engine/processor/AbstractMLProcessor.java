/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import java.util.Map;

/**
 * Abstract base class for all ML processors.
 * <p>
 * This class provides common functionality for processor implementations including
 * configuration management and validation. All concrete processor implementations
 * should extend this class to ensure consistent behavior and configuration handling.
 * <p>
 * <b>Implementation Guidelines:</b>
 * <ul>
 *   <li>Override {@link #validateConfig()} to validate processor-specific configuration</li>
 *   <li>Implement {@link #process(Object)} to define the processor's transformation logic</li>
 *   <li>Access configuration via the protected {@link #config} field</li>
 *   <li>Throw {@link IllegalArgumentException} for invalid configuration</li>
 *   <li>Return original input unchanged on processing errors (fail gracefully)</li>
 *   <li>Log errors appropriately (debug for expected, warn for unexpected)</li>
 * </ul>
 * <p>
 * <b>Configuration Pattern:</b>
 * Processors receive configuration as a Map with a required "type" field and
 * processor-specific parameters:
 * <pre>
 * {
 *   "type": "processor_type",
 *   "param1": "value1",
 *   "param2": "value2"
 * }
 * </pre>
 * <p>
 * <b>Lifecycle:</b>
 * <ol>
 *   <li>Constructor is called with configuration map</li>
 *   <li>Configuration is stored in {@link #config} field</li>
 *   <li>{@link #validateConfig()} is called automatically</li>
 *   <li>Processor is ready to process data via {@link #process(Object)}</li>
 * </ol>
 * <p>
 * <b>Thread Safety:</b>
 * Processor instances should be stateless and thread-safe. All state should be
 * derived from the immutable configuration map. Do not store mutable state in
 * instance fields that could be modified during processing.
 * <p>
 * <b>Example Implementation:</b>
 * <pre>
 * {@code
 * @Processor(MLProcessorType.EXAMPLE)
 * public class ExampleProcessor extends AbstractMLProcessor {
 *     private final String requiredParam;
 *     private final String optionalParam;
 *     
 *     public ExampleProcessor(Map<String, Object> config) {
 *         super(config);
 *         this.requiredParam = (String) config.get("required_param");
 *         this.optionalParam = (String) config.getOrDefault("optional_param", "default");
 *     }
 *     
 *     @Override
 *     protected void validateConfig() {
 *         if (!config.containsKey("required_param")) {
 *             throw new IllegalArgumentException("'required_param' is required");
 *         }
 *     }
 *     
 *     @Override
 *     public Object process(Object input) {
 *         try {
 *             // Process input
 *             return processedResult;
 *         } catch (Exception e) {
 *             log.warn("Processing failed: {}", e.getMessage());
 *             return input; // Return original on error
 *         }
 *     }
 * }
 * }
 * </pre>
 * 
 * @see MLProcessor Interface defining the process method
 * @see MLProcessorType Enum of available processor types
 * @see ProcessorChain Utility for chaining processors
 */
public abstract class AbstractMLProcessor implements MLProcessor {

    /**
     * Configuration map containing processor-specific parameters.
     * This field is immutable and should not be modified after construction.
     * Subclasses can access this field to retrieve configuration values.
     */
    protected final Map<String, Object> config;

    /**
     * Constructs a new processor with the given configuration.
     * <p>
     * This constructor stores the configuration and automatically calls
     * {@link #validateConfig()} to ensure the configuration is valid before
     * the processor is used.
     * <p>
     * Subclasses should call this constructor via {@code super(config)} and
     * then extract and store any processor-specific configuration values.
     * 
     * @param config Configuration map containing processor parameters.
     *               Must not be null. Should contain a "type" field and
     *               any processor-specific parameters.
     * @throws IllegalArgumentException if configuration validation fails
     */
    protected AbstractMLProcessor(Map<String, Object> config) {
        this.config = config;
        validateConfig();
    }

    /**
     * Validates the processor configuration.
     * <p>
     * This method is called automatically by the constructor after the configuration
     * is stored. Subclasses should override this method to validate processor-specific
     * configuration requirements.
     * <p>
     * <b>Validation Guidelines:</b>
     * <ul>
     *   <li>Check for required parameters and throw {@link IllegalArgumentException} if missing</li>
     *   <li>Validate parameter types and values</li>
     *   <li>Provide clear, descriptive error messages</li>
     *   <li>Do not perform expensive operations (validation should be fast)</li>
     * </ul>
     * <p>
     * <b>Example:</b>
     * <pre>
     * {@code
     * @Override
     * protected void validateConfig() {
     *     if (!config.containsKey("pattern")) {
     *         throw new IllegalArgumentException("'pattern' is required for regex processor");
     *     }
     *     String pattern = (String) config.get("pattern");
     *     if (pattern == null || pattern.trim().isEmpty()) {
     *         throw new IllegalArgumentException("'pattern' cannot be empty");
     *     }
     * }
     * }
     * </pre>
     * <p>
     * The default implementation does nothing. Subclasses only need to override
     * this method if they have configuration requirements to validate.
     * 
     * @throws IllegalArgumentException if the configuration is invalid
     */
    protected void validateConfig() {
        // Default implementation does nothing
        // Override in subclasses if validation is needed
    }

}
