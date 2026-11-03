/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import lombok.Getter;

/**
 * One unit of inference work (e.g. a single text doc) with its UTF-8 byte size, which the splitter
 * uses to pack items within the model's size limits. When a batch mixes items from several requests, an
 * item also carries a back-pointer to the request it came from (sourceIndex) and its position within
 * that request (positionInSource) so each result can be routed back; single-request splitting leaves
 * these at NO_SOURCE.
 */
@Getter
public class BatchItem {

    public static final int NO_SOURCE = -1;

    private final Object payload;
    private final long byteSize;
    private final int sourceIndex;
    private final int positionInSource;

    public BatchItem(Object payload, long byteSize) {
        this(payload, byteSize, NO_SOURCE, NO_SOURCE);
    }

    public BatchItem(Object payload, long byteSize, int sourceIndex, int positionInSource) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be non-negative, but got " + byteSize);
        }
        this.payload = payload;
        this.byteSize = byteSize;
        this.sourceIndex = sourceIndex;
        this.positionInSource = positionInSource;
    }
}
