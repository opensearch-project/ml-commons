/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.List;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.transport.TransportChannel;

import lombok.Getter;

/**
 * One predict request waiting in a ModelBatchQueue: its input, listener, predictor and channel, plus what
 * is derived from the input once at enqueue so flush need not re-parse it — the decomposed items, the group
 * key deciding which requests may coalesce, and the item count and byte size for the queue's running totals.
 * items and groupKey are null when the input type has no batch handler, and such an entry is rejected as
 * unsupported at flush.
 */
@Getter
public class QueueEntry {

    private final MLInput input;
    private final ActionListener<MLTaskResponse> listener;
    private final Predictable predictor;
    private final TransportChannel channel;
    private final List<BatchItem> items;
    private final String groupKey;
    private final int itemCount;
    private final long byteSize;

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
            // unknown size so it still participates in the count threshold and is drained and rejected promptly.
            this.itemCount = 1;
            this.byteSize = 0L;
        } else {
            int count = items.size();
            long bytes = 0L;
            for (BatchItem item : items) {
                bytes += item.getByteSize();
            }
            this.itemCount = count;
            this.byteSize = bytes;
        }
    }
}
