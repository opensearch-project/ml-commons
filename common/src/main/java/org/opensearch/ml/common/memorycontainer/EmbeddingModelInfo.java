/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import org.opensearch.ml.common.FunctionName;

/**
 * Info about a known embedding model — type (dense/sparse) and default dimension.
 */
public class EmbeddingModelInfo {
    public final FunctionName functionName;
    public final int dimension;

    public EmbeddingModelInfo(FunctionName functionName, int dimension) {
        this.functionName = functionName;
        this.dimension = dimension;
    }
}
