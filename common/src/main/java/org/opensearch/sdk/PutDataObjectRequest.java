/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;

public class PutDataObjectRequest extends DataObjectRequest {

    private final boolean overwriteIfExists;
    private final ToXContentObject dataObject;

    /**
     * Instantiate this request with an index and data object.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the data object may be referred to as an item.
     * @param index the index location to put the object
     * @param dataObject the data object
     */
    public PutDataObjectRequest(String index, String id, String tenantId, boolean overwriteIfExists, ToXContentObject dataObject) {
        super(index, id, tenantId);
        this.overwriteIfExists = overwriteIfExists;
        this.dataObject = dataObject;
    }

    /**
     * Returns whether to overwrite an existing document (upsert)
     * @return true if this request should overwrite
     */
    public boolean overwriteIfExists() {
        return this.overwriteIfExists;
    }

    /**
     * Returns the data object
     * @return the data object
     */
    public ToXContentObject dataObject() {
        return this.dataObject;
    }

    @Override
    public boolean isWriteRequest() {
        return true;
    }

    /**
     * Instantiate a builder for this object
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder extends DataObjectRequest.Builder<Builder> {
        private boolean overwriteIfExists = true;
        private ToXContentObject dataObject = null;

        /**
         * Specify whether to overwrite an existing document/item (upsert). True by default. 
         * @param overwriteIfExists whether to overwrite an existing document/item
         * @return the updated builder
         */
        public Builder overwriteIfExists(boolean overwriteIfExists) {
            this.overwriteIfExists = overwriteIfExists;
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
         * Add a data object as a map to this builder
         * @param dataObjectMap the data object as a map of fields
         * @return the updated builder
         */
        public Builder dataObject(Map<String, Object> dataObjectMap) {
            this.dataObject = (builder, params) -> builder.map(dataObjectMap);
            return this;
        }

        /**
         * Builds the request
         * @return A {@link PutDataObjectRequest}
         */
        public PutDataObjectRequest build() {
            return new PutDataObjectRequest(this.index, this.id, this.tenantId, this.overwriteIfExists, this.dataObject);
        }
    }
}
