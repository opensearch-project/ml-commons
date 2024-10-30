/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.Arrays;

public class BulkDataObjectResponse {

    private final DataObjectResponse[] responses;
    private final long tookInMillis;
    private final long ingestTookInMillis;

    public BulkDataObjectResponse(DataObjectResponse[] responses, long tookInMillis, long ingestTookInMillis) {
        this.responses = responses;
        this.tookInMillis = tookInMillis;
        this.ingestTookInMillis = ingestTookInMillis;
    }

    /**
     * The items representing each action performed in the bulk operation (in the same order!).
     * @return the responses in the same order requested
     */
    public DataObjectResponse[] getResponses() {
        return responses;
    }

    /**
     * How long the bulk execution took. Excluding ingest preprocessing.
     * @return the execution time in milliseconds
     */
    public long getTookInMillis() {
        return tookInMillis;
    }

    /**
     * If ingest is enabled returns the bulk ingest preprocessing time. in milliseconds, otherwise -1 is returned.
     * @return the ingest execution time in milliseconds
     */
    public long getIngestTookInMillis() {
        return ingestTookInMillis;
    }

    /**
     * Has anything failed with the execution.
     * @return true if any response failed, false otherwise
     */
    public boolean hasFailures() {
        return Arrays.stream(responses).anyMatch(DataObjectResponse::isFailed);
    }
}
