/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for Agentic Memory integration
 */
@Data
@Builder
public class AgenticMemoryConfig {

    /**
     * Memory container ID to use for storing conversations
     */
    private String memoryContainerId;

    /**
     * Whether to enable memory container integration
     * If false, falls back to ConversationIndexMemory behavior
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Whether to enable inference (long-term memory extraction)
     */
    @Builder.Default
    private boolean enableInference = true;

    /**
     * Custom namespace fields to add to memory container entries
     */
    private java.util.Map<String, String> customNamespace;

    /**
     * Custom tags to add to memory container entries
     */
    private java.util.Map<String, String> customTags;
}
