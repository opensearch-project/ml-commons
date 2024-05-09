/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class DeleteCustomRequest {

    private final String index;
    private final String id;

    /**
     * Instantiate this request with an index and id
     * @param index the index location to delete the object
     * @param id the document id
     */
    public DeleteCustomRequest(String index, String id) {
        this.index = index;
        this.id = id;
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
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private String id = null;

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
         * Builds the object
         * @return A {@link DeleteCustomRequest}
         */
        public DeleteCustomRequest build() {
            return new DeleteCustomRequest(this.index, this.id);
        }
    }
}
