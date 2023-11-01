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
     * Get tools.
     * @return A list of provided tools
     */
    List<Tool> getTools();

    /**
     * Get tool factories.
     * @return A list of tool factories
     */
    List<Tool.Factory<? extends Tool>> getToolFactories();
}
