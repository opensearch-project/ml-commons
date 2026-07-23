/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.model.BatchInferenceConfig;

/**
 * Greedily packs ordered items into sub-batches that respect the count and (optional) byte ceilings.
 * Preserves order, never drops an item, and gives an over-sized item its own sub-batch rather than
 * splitting it.
 */
public class SizeBasedBatchSplitter {

    public List<List<BatchItem>> split(List<BatchItem> items, BatchInferenceConfig config) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cannot split an empty item list");
        }
        if (config == null) {
            throw new IllegalArgumentException("BatchInferenceConfig must not be null");
        }

        int maxItems = config.getMaxItemsPerRequest();
        boolean byteLimitEnabled = config.isByteLimitEnabled();
        long maxBytes = config.getMaxBytesPerRequest();

        List<List<BatchItem>> batches = new ArrayList<>();
        List<BatchItem> current = new ArrayList<>();
        long currentBytes = 0L;

        for (BatchItem item : items) {
            boolean wouldExceedCount = current.size() + 1 > maxItems;
            boolean wouldExceedBytes = byteLimitEnabled && !current.isEmpty() && currentBytes + item.getByteSize() > maxBytes;

            // The !current.isEmpty() guard lets a single over-sized item stand alone instead of being dropped.
            if (!current.isEmpty() && (wouldExceedCount || wouldExceedBytes)) {
                batches.add(current);
                current = new ArrayList<>();
                currentBytes = 0L;
            }

            current.add(item);
            currentBytes += item.getByteSize();
        }

        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }
}
