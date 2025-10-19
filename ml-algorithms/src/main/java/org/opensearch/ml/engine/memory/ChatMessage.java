/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import java.util.Map;

/**
 * Interface for chat messages in the unified memory system.
 * Provides a common abstraction for messages across different memory implementations.
 */
public interface ChatMessage {
    /**
     * Get the role of the message sender
     * @return role such as "user", "assistant", "system"
     */
    String getRole();

    /**
     * Get the content of the message
     * @return message content
     */
    String getContent();

    /**
     * Get additional metadata associated with the message
     * @return metadata map
     */
    Map<String, Object> getMetadata();
}
