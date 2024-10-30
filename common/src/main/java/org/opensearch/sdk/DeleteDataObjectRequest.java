/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class DeleteDataObjectRequest extends DataObjectRequest {

    /**
     * Instantiate this request with an index and id.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param index the index location to delete the object
     * @param id the document id
     * @param tenantId the tenant id
     */
    public DeleteDataObjectRequest(String index, String id, String tenantId) {
        super(index, id, tenantId);
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

        /**
         * Builds the object
         * @return A {@link DeleteDataObjectRequest}
         */
        public DeleteDataObjectRequest build() {
            return new DeleteDataObjectRequest(this.index, this.id, this.tenantId);
        }
    }
}
