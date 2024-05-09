/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class DeleteCustomResponse {
    private final String id;
    private final boolean deleted;

    public DeleteCustomResponse(String id, boolean deleted) {
        this.id = id;
        this.deleted = deleted;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns whether deletion was successful
     * @return true if deletion was successful, false if the object was not found
     */
    public boolean deleted() {
        return deleted;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private boolean deleted = false;

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
         * Add a deleted status to this builder
         * @param deleted the deleted status to add
         * @return the updated builder
         */
        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link DeleteCustomResponse}
         */
        public DeleteCustomResponse build() {
            return new DeleteCustomResponse(this.id, this.deleted);
        }
    }
}
