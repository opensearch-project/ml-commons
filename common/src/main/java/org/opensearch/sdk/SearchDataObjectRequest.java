/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.search.builder.SearchSourceBuilder;

public class SearchDataObjectRequest {

    private final String[] indices;
    private final SearchSourceBuilder searchSourceBuilder;

    /**
     * Instantiate this request with an index and id
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param indices the indices to search for the object
     * @param searchSourceBuilder the context to use when fetching _source
     */
    public SearchDataObjectRequest(String[] indices, SearchSourceBuilder searchSourceBuilder) {
        this.indices = indices;
        this.searchSourceBuilder = searchSourceBuilder;
    }

    /**
     * Returns the indices
     * @return the indices
     */
    public String[] indices() {
        return this.indices;
    }

    /**
     * Returns the builder for searching
     * @return the SearchSourceBuilder
     */
    public SearchSourceBuilder searchSourceBuilder() {
        return this.searchSourceBuilder;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String[] indices = null;
        private SearchSourceBuilder searchSourceBuilder;

        /**
         * Empty Constructor for the Builder object
         */
        public Builder() {}

        /**
         * Add a indices to this builder
         * @param indices the index to put the object
         * @return the updated builder
         */
        public Builder indices(String... indices) {
            this.indices = indices;
            return this;
        }

        /**
         * Add a SearchSourceBuilder to this builder
         * @param searchSourceBuilder the searchSourceBuilder
         * @return the updated builder
         */
        public Builder searchSourceBuilder(SearchSourceBuilder searchSourceBuilder) {
            this.searchSourceBuilder = searchSourceBuilder;
            
            return this;
        }

        /**
         * Builds the request
         * @return A {@link SearchDataObjectRequest}
         */
        public SearchDataObjectRequest build() {
            return new SearchDataObjectRequest(this.indices, this.searchSourceBuilder);
        }
    }
}
