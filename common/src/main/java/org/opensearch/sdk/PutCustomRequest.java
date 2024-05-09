/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class PutCustomRequest {

    private final String index;
    private final Custom custom;

    /**
     * Instantiate this request with an index and custom object
     * @param index the index location to put the object
     * @param custom the custom object
     */
    public PutCustomRequest(String index, Custom custom) {
        this.index = index;
        this.custom = custom;
    }

    /**
     * Returns the index
     * @return the index
     */
    public String index() {
        return this.index;
    }

    /**
     * Returns the custom object
     * @return the custom object
     */
    public Custom custom() {
        return this.custom;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private Custom custom = null;

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
         * Add a Custom object to this builder
         * @param custom the Custom object
         * @return the updated builder
         */
        public Builder custom(Custom custom) {
            this.custom = custom;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link PutCustomRequest}
         */
        public PutCustomRequest build() {
            return new PutCustomRequest(this.index, this.custom);
        }
    }
}
