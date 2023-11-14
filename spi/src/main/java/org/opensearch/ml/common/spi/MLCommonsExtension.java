/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi;

import org.opensearch.ml.common.spi.tools.Tool;

import java.util.List;

/**
 * ml-commons extension interface.
 */
public interface MLCommonsExtension {

    /**
     * Get tool factories.
     * @return A list of tool factories
     */
    List<Tool> getTools();
}
