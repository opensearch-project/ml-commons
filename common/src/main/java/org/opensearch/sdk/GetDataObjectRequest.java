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

public class GetDataObjectRequest {

    private final String index;
    private final String id;
    private final String tenantId;
    private final FetchSourceContext fetchSourceContext;

    /**
     * Instantiate this request with an index and id
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param index the index location to get the object
     * @param id the document id
     * @param fetchSourceContext the context to use when fetching _source
     */
    public GetDataObjectRequest(String index, String id, String tenantId, FetchSourceContext fetchSourceContext) {
        this.index = index;
        this.id = id;
        this.tenantId = tenantId;
        this.fetchSourceContext = fetchSourceContext;
    }

    /**
     * Returns the index
     * @return the index
     */
    public String index() {
        return this.index;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return this.id;
    }

    public String tenantId() {
        return this.tenantId;
    }

    /**
     * Returns the context for fetching _source
     * @return the fetchSourceContext
     */
    public FetchSourceContext fetchSourceContext() {
        return this.fetchSourceContext;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private String id = null;
        private String tenantId = null;
        private FetchSourceContext fetchSourceContext;

        /**
         * Empty Constructor for the Builder object
         */
        public Builder() {}

        /**
         * Add an index to this builder
         * @param index the index to put the object
         * @return the updated builder
         */
        public Builder index(String index) {
            this.index = index;
            return this;
        }

        /**
         * Add an id to this builder
         * @param id the document id
         * @return the updated builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

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
