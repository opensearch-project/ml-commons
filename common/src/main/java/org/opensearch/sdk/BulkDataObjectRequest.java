/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensearch.action.support.WriteRequest.RefreshPolicy;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.Strings;

public class BulkDataObjectRequest {

    private final List<DataObjectRequest> requests = new ArrayList<>();
    private final Set<String> indices = new HashSet<>();
    private RefreshPolicy refreshPolicy = RefreshPolicy.NONE;
    private String globalIndex;

    /**
     * Instantiate this request with a global index.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param globalIndex the index location for all the bulk requests as a default if not already specified
     */
    public BulkDataObjectRequest(@Nullable String globalIndex) {
        this.globalIndex = globalIndex;
    }

    /**
     * Returns the list of requests in this bulk request.
     * @return the requests list
     */
    public List<DataObjectRequest> requests() {
        return List.copyOf(this.requests);
    }

    /**
     * Returns the indices being updated in this bulk request.
     * @return the indices being updated
     */
    public Set<String> getIndices() {
        return Collections.unmodifiableSet(indices);
    }

    /**
     * Add the given request to the {@link BulkDataObjectRequest}
     * @param request The request to add
     * @return the updated request object
     */
    public BulkDataObjectRequest add(DataObjectRequest request) {
        if (!request.isWriteRequest()) {
            throw new IllegalArgumentException("No support for request [" + request.getClass().getName() + "]");
        }
        if (Strings.isNullOrEmpty(request.index())) {
            if (Strings.isNullOrEmpty(globalIndex)) {
                throw new IllegalArgumentException(
                    "Either the request [" + request.getClass().getName() + "] or the bulk request must specify an index."
                );
            }
            indices.add(globalIndex);
            request.index(globalIndex);
        } else {
            indices.add(request.index());
        }
        requests.add(request);
        return this;
    }

    /**
     * Should this request trigger a refresh ({@linkplain RefreshPolicy#IMMEDIATE}), wait for a refresh (
     * {@linkplain RefreshPolicy#WAIT_UNTIL}), or proceed ignore refreshes entirely ({@linkplain RefreshPolicy#NONE}, the default).
     */
    public BulkDataObjectRequest setRefreshPolicy(RefreshPolicy refreshPolicy) {
        this.refreshPolicy = refreshPolicy;
        return this;
    }

    /**
     * Should this request trigger a refresh ({@linkplain RefreshPolicy#IMMEDIATE}), wait for a refresh (
     * {@linkplain RefreshPolicy#WAIT_UNTIL}), or proceed ignore refreshes entirely ({@linkplain RefreshPolicy#NONE}, the default).
     */
    public RefreshPolicy getRefreshPolicy() {
        return refreshPolicy;
    }
    
    /**
     * Instantiate a builder for this object
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String globalIndex = null;

        /**
         * Empty constructor to initialize
         */
        protected Builder() {}

        /**
         * Add an index to this builder
         * @param index the index to put the object
         * @return the updated builder
         */
        public Builder globalIndex(String index) {
            this.globalIndex = index;
            return this;
        }

        /**
         * Builds the request
         * @return A {@link BulkDataObjectRequest}
         */
        public BulkDataObjectRequest build() {
            return new BulkDataObjectRequest(this.globalIndex);
        }
    }
}
