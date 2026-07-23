/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.EnumMap;
import java.util.Map;

import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.input.MLInput;

/**
 * Maps an input dataset type to its batching handler. An input type without a registered handler is
 * not batched, and lookups for it return null.
 */
public class BatchableInputRegistry {

    private final Map<MLInputDataType, BatchableInput> handlers;

    public BatchableInputRegistry() {
        this.handlers = new EnumMap<>(MLInputDataType.class);
        this.handlers.put(MLInputDataType.TEXT_DOCS, new TextDocsBatchableInput());
    }

    public BatchableInput get(MLInput input) {
        if (input == null || input.getInputDataset() == null) {
            return null;
        }
        return handlers.get(input.getInputDataset().getInputDataType());
    }
}
