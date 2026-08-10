/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import lombok.Getter;

/**
 * One unit of inference work (e.g. a single text doc) with its UTF-8 byte size, which the splitter
 * uses to pack items within the model's size limits.
 */
@Getter
public class BatchItem {

    private final Object payload;
    private final long byteSize;

    public BatchItem(Object payload, long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be non-negative, but got " + byteSize);
        }
        this.payload = payload;
        this.byteSize = byteSize;
    }
}
