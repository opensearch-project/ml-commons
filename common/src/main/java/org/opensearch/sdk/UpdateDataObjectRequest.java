/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.core.xcontent.ToXContentObject;

public class UpdateDataObjectRequest {

    private final String index;
    private final String id;
    private final String tenantId;
    private final ToXContentObject dataObject;

    /**
     * Instantiate this request with an index and data object.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the data object may be referred to as an item.
     * @param index the index location to update the object
     * @param id the document id
     * @param tenantId the tenant id
     * @param dataObject the data object
     */
    public UpdateDataObjectRequest(String index, String id, String tenantId, ToXContentObject dataObject) {
        this.index = index;
        this.id = id;
        this.tenantId = tenantId;
        this.dataObject = dataObject;
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
     * Returns the data object
     * @return the data object
     */
    public ToXContentObject dataObject() {
        return this.dataObject;
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder {
        private String index = null;
        private String id = null;
        private String tenantId = null;
        private ToXContentObject dataObject = null;

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
         * Add a tenant ID to this builder
         * @param tenantId the tenant id
         * @return the updated builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
      
        /**
         * Add a data object to this builder
         * @param dataObject the data object
         * @return the updated builder
         */
        public Builder dataObject(ToXContentObject dataObject) {
            this.dataObject = dataObject;
            return this;
        }

        /**
         * Builds the request
         * @return A {@link UpdateDataObjectRequest}
         */
        public UpdateDataObjectRequest build() {
            return new UpdateDataObjectRequest(this.index, this.id, this.tenantId, this.dataObject);
        }
    }
}
