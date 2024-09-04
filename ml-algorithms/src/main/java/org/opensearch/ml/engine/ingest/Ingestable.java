/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;

public interface Ingestable {
    /**
     * offline ingest data with given input.
     * @param mlBatchIngestionInput batch ingestion input data
     * @return successRate (0 - 100)
     */
    default double ingest(MLBatchIngestionInput mlBatchIngestionInput) {
        throw new IllegalStateException("Ingest is not implemented");
    }
}
