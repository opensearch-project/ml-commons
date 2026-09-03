/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.transport.TransportChannel;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * One predict request waiting in a ModelBatchQueue: its input, listener, predictor and channel, plus what
 * is derived from the input once at enqueue so flush need not re-parse it — the decomposed items, the group
 * key deciding which requests may coalesce, the item count and payload byte size for the queue's batching
 * thresholds, and an estimated retained byte size for node-level memory backpressure. items and groupKey are
 * null when the input type has no batch handler, and such an entry is rejected as unsupported at flush.
 */
@Getter
public class QueueEntry {

    static final long ESTIMATED_ENTRY_OVERHEAD_BYTES = 1_024L;
    static final long ESTIMATED_ITEM_OVERHEAD_BYTES = 64L;

    private final MLInput input;
    private final ActionListener<MLTaskResponse> listener;
    private final Predictable predictor;
    private final TransportChannel channel;
    private final List<BatchItem> items;
    private final String groupKey;
    private final int itemCount;
    private final long payloadByteSize;
    private final long retainedByteSize;
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean budgetReleased = new AtomicBoolean(false);

    public QueueEntry(
        MLInput input,
        ActionListener<MLTaskResponse> listener,
        Predictable predictor,
        TransportChannel channel,
        List<BatchItem> items,
        String groupKey
    ) {
        this.input = input;
        this.listener = listener;
        this.predictor = predictor;
        this.channel = channel;
        this.items = items;
        this.groupKey = groupKey;
        if (items == null) {
            // No batch handler for this input type, so it can't be decomposed; count it as one request with
            // unknown payload size so it still participates in the count threshold and is drained and rejected.
            // Charge a non-zero retained size so unsupported requests cannot bypass the node memory budget.
            this.itemCount = 1;
            this.payloadByteSize = 0L;
            this.retainedByteSize = ESTIMATED_ENTRY_OVERHEAD_BYTES;
        } else {
            int count = items.size();
            long bytes = 0L;
            for (BatchItem item : items) {
                bytes = Math.addExact(bytes, item.getByteSize());
            }
            this.itemCount = count;
            this.payloadByteSize = bytes;
            this.retainedByteSize = Math
                .addExact(
                    Math.addExact(bytes, ESTIMATED_ENTRY_OVERHEAD_BYTES),
                    Math.multiplyExact((long) count, ESTIMATED_ITEM_OVERHEAD_BYTES)
                );
        }
    }

    boolean markBudgetReleased() {
        return budgetReleased.compareAndSet(false, true);
    }
}
