/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.parser;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.engine.processor.ProcessorChain;

/**
 * Helper class for tool output processing
 */
public class ToolParser {

    /**
     * Create a parser that uses output processors
     * @param baseParser Base parser to extract initial result
     * @param processorConfigs Processor configurations
     * @return Parser with output processing
     */
    public static Parser createProcessingParser(Parser baseParser, List<Map<String, Object>> processorConfigs) {
        ProcessorChain processorChain = new ProcessorChain(processorConfigs);

        return o -> {
            // Apply base parser first
            Object baseResult = o;
            if (baseParser != null) {
                baseResult = baseParser.parse(o);
            }

            // Apply output processors if any
            if (processorChain.hasProcessors()) {
                return processorChain.process(baseResult);
            }

            return baseResult;
        };
    }

    /**
     * Create output parser for a tool from tool parameters
     * @param params Tool parameters containing output processor configurations
     * @param baseParser Base parser that extracts initial result
     * @return Parser with output processing applied
     */
    public static Parser createFromToolParams(Map<String, Object> params, Parser baseParser) {
        List<Map<String, Object>> processorConfigs = ProcessorChain.extractProcessorConfigs(params);
        return createProcessingParser(baseParser, processorConfigs);
    }

    public static Parser createFromToolParams(Map<String, Object> params) {
        return createFromToolParams(params, null);
    }
}
