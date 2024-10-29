/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public abstract class DataObjectRequest {

    private String index;
    private final String id;
    private final String tenantId;
    
    /**
     * Instantiate this request with an index and id.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param index the index location to delete the object
     * @param id the document id
     * @param tenantId the tenant id
     */
    protected DataObjectRequest(String index, String id, String tenantId) {
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
     * Sets the index
     * @param index The new index to set
     */
    public void index(String index) {
        this.index = index;
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
     * Returns whether the subclass can be used in a {@link BulkDataObjectRequest}
     * @return
     */
    public abstract boolean isWriteRequest();

    /**
     * Superclass for common fields in subclass builders
     */
    public static class Builder<T extends Builder<T>> {
        protected String index = null;
        protected String id = null;
        protected String tenantId = null;

        /**
         * Empty constructor to initialize
         */
        protected Builder() {}

        /**
         * Add an index to this builder
         * @param index the index to put the object
         * @return the updated builder
         */
        public T index(String index) {
            this.index = index;
            return self();
        }

        /**
         * Add an id to this builder
         * @param id the document id
         * @return the updated builder
         */
        public T id(String id) {
            this.id = id;
            return self();
        }

        /**
         * Add a tenant id to this builder
         * @param tenantId the tenant id
         * @return the updated builder
         */
        public T tenantId(String tenantId) {
            this.tenantId = tenantId;
            return self();
        }

        /**
         * Returns this builder as the parameterized type.
         */
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
    }
}
