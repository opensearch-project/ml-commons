/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi.memory;

/**
 * General message interface.
 */
public interface Message {

    /**
     * Get message type.
     * @return
     */
    String getType();

    /**
     * Get message content.
     * @return
     */
    String getContent();
}
