package org.opensearch.ml.common.spi;

import org.opensearch.ml.common.spi.tools.Tool;

import java.util.List;

/**
 * ml-commons extension interface.
 */
public interface MLCommonsExtension {

    /**
     * Get tools.
     * @return
     */
    List<Tool> getTools();

    /**
     * Get tool factories.
     * @return
     */
    List<Tool.Factory> getToolFactories();
}
