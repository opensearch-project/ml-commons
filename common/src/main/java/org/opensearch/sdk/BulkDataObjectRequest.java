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

import org.opensearch.common.Nullable;
import org.opensearch.core.common.Strings;

public class BulkDataObjectRequest {

    private final List<DataObjectRequest> requests = new ArrayList<>();
    private final Set<String> indices = new HashSet<>();
    private String globalIndex;
    private String globalTenantId;

    /**
     * Instantiate this request with a global index.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param globalIndex the index location for all the bulk requests as a default if not already specified
     * @param globalTenantId the tenantId for all the bulk requests, overwriting what's specified if not null
     */
    public BulkDataObjectRequest(@Nullable String globalIndex, @Nullable String globalTenantId) {
        this.globalIndex = globalIndex;
        this.globalTenantId = globalTenantId;
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
     * Return the global tenant id to be applied to all requests
     * @return the globalTenantId
     */
    public String globalTenantId() {
        return this.globalTenantId;
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
        if (!Strings.isNullOrEmpty(globalTenantId)) {
            request.tenantId(globalTenantId);
        }
        requests.add(request);
        return this;
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
        private String globalTenantId = null;

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
         * Add a tenant id to this builder
         * @param tenantId the tenant id
         * @return the updated builder
         */
        public Builder globalTenantId(String tenantId) {
            this.globalTenantId = tenantId;
            return this;
        }

        /**
         * Builds the request
         * @return A {@link BulkDataObjectRequest}
         */
        public BulkDataObjectRequest build() {
            return new BulkDataObjectRequest(this.globalIndex, this.globalTenantId);
        }
    }
}
