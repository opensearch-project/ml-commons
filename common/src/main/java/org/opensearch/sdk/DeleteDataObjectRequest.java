/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class DeleteDataObjectRequest {

    private final String index;
    private final String id;
    private final String tenantId;

    /**
     * Instantiate this request with an index and id.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param index the index location to delete the object
     * @param id the document id
     */
    public DeleteDataObjectRequest(String index, String id, String tenantId) {
        this.index = index;
        this.id = id;
        this.tenantId = tenantId;
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
     * Returns the tenant id
     * @return the tenantId
     */
    public String tenantId() {
        return this.tenantId;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private String id = null;
        private String tenantId = null;

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
         * Add a tenant id to this builder
         * @param tenantId the tenant id
         * @return the updated builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link DeleteDataObjectRequest}
         */
        public DeleteDataObjectRequest build() {
            return new DeleteDataObjectRequest(this.index, this.id, this.tenantId);
        }
    }
}
