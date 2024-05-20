/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class PutDataObjectResponse {
    private final String id;
    private final boolean created;

    /**
     * Instantiate this request with an id and creation status.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param created Whether the object was created.
     */
    public PutDataObjectResponse(String id, boolean created) {
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
         * @return A {@link PutDataObjectResponse}
         */
        public PutDataObjectResponse build() {
            return new PutDataObjectResponse(this.id, this.created);
        }
    }
}
