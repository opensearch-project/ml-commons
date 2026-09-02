/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.concurrent.atomic.AtomicLong;

class QueueMemoryBudget {

    private volatile long maxBytes;
    private final AtomicLong reservedBytes = new AtomicLong();

    QueueMemoryBudget(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    long getMaxBytes() {
        return maxBytes;
    }

    long getReservedBytes() {
        return reservedBytes.get();
    }

    boolean tryReserve(long bytes) {
        if (bytes <= 0) {
            return true;
        }
        if (reservedBytes.addAndGet(bytes) > maxBytes) {
            reservedBytes.addAndGet(-bytes);
            return false;
        }
        return true;
    }

    void release(long bytes) {
        if (bytes > 0) {
            reservedBytes.addAndGet(-bytes);
        }
    }
}
