/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.search.fetch.subphase.FetchSourceContext;

public class GetDataObjectRequest extends DataObjectRequest {

    private final FetchSourceContext fetchSourceContext;

    /**
     * Instantiate this request with an index and id
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param index the index location to get the object
     * @param id the document id
     * @param tenantId the tenant id
     * @param fetchSourceContext the context to use when fetching _source
     */
    public GetDataObjectRequest(String index, String id, String tenantId, FetchSourceContext fetchSourceContext) {
        super(index, id, tenantId);
        this.fetchSourceContext = fetchSourceContext;
    }

    /**
     * Returns the context for fetching _source
     * @return the fetchSourceContext
     */
    public FetchSourceContext fetchSourceContext() {
        return this.fetchSourceContext;
    }

    @Override
    public boolean isWriteRequest() {
        return false;
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
    public static class Builder extends DataObjectRequest.Builder<Builder> {
        private FetchSourceContext fetchSourceContext;

        /**
         * Add a fetchSourceContext to this builder
         * @param fetchSourceContext the fetchSourceContext
         * @return the updated builder
         */
        public Builder fetchSourceContext(FetchSourceContext fetchSourceContext) {
            this.fetchSourceContext = fetchSourceContext;
            return this;
        }

        /**
         * Builds the request
         * @return A {@link GetDataObjectRequest}
         */
        public GetDataObjectRequest build() {
            return new GetDataObjectRequest(this.index, this.id, this.tenantId, this.fetchSourceContext);
        }
    }
}
