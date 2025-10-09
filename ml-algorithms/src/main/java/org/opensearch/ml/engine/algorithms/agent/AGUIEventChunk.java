/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.nio.ByteBuffer;

import org.opensearch.common.lease.Releasable;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.http.HttpChunk;

/**
 * HTTP chunk implementation for AG-UI events in streaming responses.
 */
public class AGUIEventChunk implements HttpChunk {

    private final BytesReference content;
    private final boolean isLast;

    public AGUIEventChunk(String sseData, boolean isLast) {
        this.content = BytesReference.fromByteBuffer(ByteBuffer.wrap(sseData.getBytes()));
        this.isLast = isLast;
    }

    @Override
    public void close() {
        if (content instanceof Releasable) {
            ((Releasable) content).close();
        }
    }

    @Override
    public boolean isLast() {
        return isLast;
    }

    @Override
    public BytesReference content() {
        return content;
    }
}
