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

public class GetCustomRequest {

    private final String index;
    private final String id;
    private final FetchSourceContext fetchSourceContext;
    private final Class<? extends Custom> clazz;

    /**
     * Instantiate this request with an index and id
     * @param index the index location to get the object
     * @param id the document id
     * @param fetchSourceContext the context to use when fetching _source
     * @param clazz the type of Custom object to parse
     */
    public GetCustomRequest(String index, String id, FetchSourceContext fetchSourceContext, Class<? extends Custom> clazz) {
        this.index = index;
        this.id = id;
        this.fetchSourceContext = fetchSourceContext;
        this.clazz = clazz;
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

    /**
     * Returns the context for fetching _source
     * @return the fetchSourceContext
     */
    public FetchSourceContext fetchSourceContext() {
        return this.fetchSourceContext;
    }
    
    /**
     * Returns the Custom Class type
     * @return the class type
     */
    public Class<? extends Custom> clazz() {
        return this.clazz;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private String id = null;
        private FetchSourceContext fetchSourceContext;
        private Class<? extends Custom> clazz = null;

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

        /**
         * Add a class type to this builder
         * @param clazz the class type
         * @return the updated builder
         */
        public Builder clazz(Class<? extends Custom> clazz) {
            this.clazz = clazz;
            return this;
        }

        public Builder fetchSourceContext(FetchSourceContext fetchSourceContext) {
            this.fetchSourceContext = fetchSourceContext;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link GetCustomRequest}
         */
        public GetCustomRequest build() {
            return new GetCustomRequest(this.index, this.id, this.fetchSourceContext, this.clazz);
        }

    }
}
