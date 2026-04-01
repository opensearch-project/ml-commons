/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memory;

/**
 * General message interface.
 */
public interface Message {

    /**
     * Get message type.
     * @return message type
     */
    String getType();

    /**
     * Get message content.
     * @return message content
     */
    String getContent();
}
