/*
* Copyright OpenSearch Contributors
* SPDX-License-Identifier: Apache-2.0
*/

package org.opensearch.ml.engine.processor;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.engine.MLEngineClassLoader;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for creating and managing chains of ML processors.
 * <p>
 * This class provides functionality for building processor chains from configuration,
 * applying processors sequentially to data, and managing processor lifecycle. Processor
 * chains enable complex data transformations by composing simple, focused processors.
 * <p>
 * <b>Processing Flow:</b>
 * <ol>
 *   <li>Input is passed to the first processor</li>
 *   <li>Each processor's output becomes the next processor's input</li>
 *   <li>The final processor's output is returned</li>
 *   <li>If any processor fails, it returns its input unchanged</li>
 * </ol>
 * <p>
 *
 * @see MLProcessor Interface for individual processors
 * @see MLProcessorType Enum of available processor types
 */
@Log4j2
public class ProcessorChain {

    /**
     * Configuration key for output processors in tool parameters.
     * Used to extract processor configurations from parameter maps.
     */
    public static final String OUTPUT_PROCESSORS = "output_processors";

    /**
     * List of processors to apply sequentially.
     */
    private final List<MLProcessor> processors;

    /**
     * Creates a list of processor instances from configuration maps.
     * <p>
     * Each configuration map must contain a "type" field specifying the processor type.
     * Additional fields are passed as configuration parameters to the processor constructor.
     * <p>
     * Example configuration:
     * <pre>
     * [
     *   {"type": "extract_json", "extract_type": "object"},
     *   {"type": "keep_fields", "fields": ["name", "email"]},
     *   {"type": "to_string"}
     * ]
     * </pre>
     * 
     * @param processorConfigs List of processor configuration maps. Each map must have a "type" field.
     * @return List of instantiated processors in the same order as configurations.
     *         Returns empty list if input is null or empty.
     * @throws IllegalArgumentException if a processor type is invalid or instantiation fails
     */
    public static List<MLProcessor> createProcessingChain(List<Map<String, Object>> processorConfigs) {
        if (processorConfigs == null || processorConfigs.isEmpty()) {
            return Collections.emptyList();
        }

        List<MLProcessor> processors = new ArrayList<>();
        for (Map<String, Object> config : processorConfigs) {
            MLProcessorType type = MLProcessorType.fromString((String) config.get("type"));
            MLProcessor mlProcessor = MLEngineClassLoader.initInstance(type, config, Map.class);
            processors.add(mlProcessor);
        }

        return processors;
    }

    /**
     * Parses processor configurations from various input formats.
     * <p>
     * Accepts configurations in multiple formats:
     * <ul>
     *   <li>Single processor config as a Map</li>
     *   <li>Multiple processor configs as a List of Maps</li>
     *   <li>Null (returns empty list)</li>
     * </ul>
     * <p>
     * This method is commonly used by processors that accept nested processor configurations,
     * such as {@code conditional} and {@code process_and_set}.
     * 
     * @param config Processor configuration(s). Can be a Map (single processor),
     *               List of Maps (multiple processors), or null.
     * @return List of instantiated processors. Returns empty list if config is null or invalid.
     */
    @SuppressWarnings("unchecked")
    public static List<MLProcessor> parseProcessorConfigs(Object config) {
        if (config == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> processorConfigs;
        if (config instanceof Map) {
            processorConfigs = Collections.singletonList((Map<String, Object>) config);
        } else if (config instanceof List) {
            processorConfigs = (List<Map<String, Object>>) config;
        } else {
            log.warn("Invalid processor configuration type: {}. Expected Map or List.", config.getClass().getSimpleName());
            return Collections.emptyList();
        }

        return createProcessingChain(processorConfigs);
    }

    /**
     * Applies a list of processors sequentially to an input object.
     * <p>
     * Each processor's output becomes the input to the next processor, creating a
     * processing pipeline. If any processor fails (returns input unchanged), processing
     * continues with the remaining processors.
     * <p>
     * Example:
     * <pre>
     * Object input = "{\"name\": \"John\", \"age\": 30}";
     * List&lt;MLProcessor&gt; processors = Arrays.asList(
     *     new MLExtractJsonProcessor(config1),
     *     new MLKeepFieldsProcessor(config2),
     *     new MLToStringProcessor(config3)
     * );
     * Object result = MLProcessorChain.applyProcessors(input, processors);
     * // Result: "{\"name\":\"John\"}"
     * </pre>
     * 
     * @param input The initial input object to process
     * @param processors List of processors to apply in order
     * @return The final processed result after all processors have been applied.
     *         Returns the original input if processors list is empty.
     */
    public static Object applyProcessors(Object input, List<MLProcessor> processors) {
        Object result = input;
        for (MLProcessor processor : processors) {
            result = processor.process(result);
        }
        return result;
    }

    /**
     * Creates a processor chain from configuration maps.
     * <p>
     * This constructor is useful when you have processor configurations from external
     * sources (e.g., API requests, configuration files) and want to create a reusable
     * processor chain instance.
     * 
     * @param processorConfigs List of processor configuration maps. Each map must contain
     *                        a "type" field and any processor-specific parameters.
     */
    public ProcessorChain(List<Map<String, Object>> processorConfigs) {
        this.processors = createProcessingChain(processorConfigs);
    }

    /**
     * Creates a processor chain from pre-instantiated processor instances.
     * <p>
     * This constructor is useful for testing or when you want to compose processors
     * programmatically without configuration maps.
     * <p>
     * Example:
     * <pre>
     * MLProcessorChain chain = new MLProcessorChain(
     *     new MLExtractJsonProcessor(config1),
     *     new MLKeepFieldsProcessor(config2),
     *     new MLToStringProcessor(config3)
     * );
     * </pre>
     * 
     * @param processors Varargs of processor instances to chain together
     */
    public ProcessorChain(MLProcessor... processors) {
        this.processors = Arrays.asList(processors);
    }

    /**
     * Processes input through the chain of processors sequentially.
     * <p>
     * Each processor's output becomes the next processor's input. If the chain is empty,
     * returns the input unchanged.
     * 
     * @param input Input object to process. Can be any type.
     * @return The final processed result after all processors have been applied
     */
    public Object process(Object input) {
        Object result = input;
        for (MLProcessor processor : processors) {
            result = processor.process(result);
        }
        return result;
    }

    /**
     * Checks if this chain contains any processors.
     * <p>
     * Useful for conditional logic to avoid unnecessary processing when no processors
     * are configured.
     * 
     * @return true if the chain has at least one processor, false otherwise
     */
    public boolean hasProcessors() {
        return !processors.isEmpty();
    }

    /**
     * Extracts processor configurations from tool parameters.
     * <p>
     * This method looks for the {@link #OUTPUT_PROCESSORS} key in the parameters map
     * and parses the value into processor configurations. Supports multiple formats:
     * <ul>
     *   <li>List of Maps - Direct processor configurations</li>
     *   <li>JSON String - Parses JSON array of processor configurations</li>
     * </ul>
     * <p>
     * Example parameter formats:
     * <pre>
     * // Format 1: List of Maps
     * Map.of("output_processors", Arrays.asList(
     *     Map.of("type", "extract_json"),
     *     Map.of("type", "to_string")
     * ))
     * 
     * // Format 2: JSON String
     * Map.of("output_processors", "[{\"type\": \"extract_json\"}, {\"type\": \"to_string\"}]")
     * </pre>
     * 
     * @param params Tool parameters map that may contain processor configurations
     * @return List of processor configuration maps. Returns empty list if:
     *         <ul>
     *           <li>params is null</li>
     *           <li>OUTPUT_PROCESSORS key is not present</li>
     *           <li>Configuration format is invalid</li>
     *           <li>JSON parsing fails</li>
     *         </ul>
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractProcessorConfigs(Map<String, ?> params) {
        if (params == null || !params.containsKey(OUTPUT_PROCESSORS)) {
            return Collections.emptyList();
        }

        Object configObj = params.get(OUTPUT_PROCESSORS);
        if (configObj instanceof List) {
            return (List<Map<String, Object>>) configObj;
        }

        if (configObj instanceof String) {
            String configStr = (String) configObj;
            try {
                List<Map<String, Object>> processorConfigs = gson.fromJson(configStr, new TypeToken<List<Map<String, Object>>>() {
                }.getType());

                if (processorConfigs != null) {
                    return processorConfigs;
                } else {
                    log.warn("Failed to parse output processor config: null result from JSON parsing");
                }
            } catch (JsonSyntaxException e) {
                log.error("Invalid JSON format in output processor configuration: {}", configStr, e);
            } catch (Exception e) {
                log.error("Error parsing output processor configuration: {}", configStr, e);
            }
        }

        return Collections.emptyList();
    }
}
