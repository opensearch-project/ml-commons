/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.ml.common.model.BatchInferenceConfig;

/**
 * Greedily packs ordered items into sub-batches that respect the count and byte ceilings, either of
 * which may be disabled.
 * Preserves order, never drops an item, and gives an over-sized item its own sub-batch rather than
 * splitting it.
 */
public class BatchSplitter {

    // Matches ArrayList's own default, used when the item limit is disabled and no bound is known.
    private static final int DEFAULT_CAPACITY = 10;

    public List<List<BatchItem>> split(List<BatchItem> items, BatchInferenceConfig config) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cannot split an empty item list");
        }
        if (config == null) {
            throw new IllegalArgumentException("BatchInferenceConfig must not be null");
        }

        boolean itemLimitEnabled = config.isItemLimitEnabled();
        int maxItems = config.getMaxItemsPerRequest();
        boolean byteLimitEnabled = config.isByteLimitEnabled();
        long maxBytes = config.getMaxBytesPerRequest();

        // With an item limit, both the number of sub-batches and the size of each one have known upper
        // bounds, so the lists can be sized up front instead of growing by reallocation. Without one,
        // neither bound is known before scanning the items, so fall back to the default capacity.
        int batchCount = itemLimitEnabled ? (items.size() + maxItems - 1) / maxItems : DEFAULT_CAPACITY;
        int batchCapacity = itemLimitEnabled ? Math.min(maxItems, items.size()) : DEFAULT_CAPACITY;

        List<List<BatchItem>> batches = new ArrayList<>(batchCount);
        List<BatchItem> current = new ArrayList<>(batchCapacity);
        long currentBytes = 0L;

        for (BatchItem item : items) {
            boolean wouldExceedCount = itemLimitEnabled && current.size() + 1 > maxItems;
            boolean wouldExceedBytes = byteLimitEnabled && !current.isEmpty() && currentBytes + item.getByteSize() > maxBytes;

            // The !current.isEmpty() guard lets a single over-sized item stand alone instead of being dropped.
            if (!current.isEmpty() && (wouldExceedCount || wouldExceedBytes)) {
                batches.add(current);
                current = new ArrayList<>(batchCapacity);
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
