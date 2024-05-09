/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class GetCustomResponse {
    private final String id;
    private final Custom custom;

    public GetCustomResponse(String id, Custom custom) {
        this.id = id;
        this.custom = custom;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return this.id;
    }

    /**
     * Returns the custom object
     * @return the custom object
     */
    public Custom custom() {
        return this.custom;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private Custom custom = null;

        /**
         * Empty Constructor for the Builder object
         */
        public Builder() {}

        /**
         * Add an id to this builder
         * @param id the id to add
         * @return the updated builder
         */
        public Builder id(String id) {
            this.id = id;
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
         * @return A {@link GetCustomResponse}
         */
        public GetCustomResponse build() {
            return new GetCustomResponse(this.id, this.custom);
        }
    }
}
