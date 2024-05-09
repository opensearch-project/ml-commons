/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class PutCustomResponse {
    private final String id;
    private final boolean created;

    public PutCustomResponse(String id, boolean created) {
        this.id = id;
        this.created = created;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns whether creation was successful
     * @return true if creation was successful
     */
    public boolean created() {
        return created;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private boolean created = false;

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
         * Add a created status to this builder
         * @param created the created status to add
         * @return the updated builder
         */
        public Builder created(boolean created) {
            this.created = created;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link PutCustomResponse}
         */
        public PutCustomResponse build() {
            return new PutCustomResponse(this.id, this.created);
        }
    }
}
