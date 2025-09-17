/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi.tools;

import java.util.List;

/**
 * General tool interface.
 */
public interface WithModelTool extends Tool {
    /**
     * Tool factory which can create instance of {@link Tool}.
     * @param <T> The subclass this factory produces
     */
    interface Factory<T extends WithModelTool> extends Tool.Factory<T> {
        /**
         * Get model id related field names
         * @return the list of all model id related field names
         */
        List<String> getAllModelKeys();
    }
}
