/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

public class GetDataObjectResponse {
    private final String id;
    private final DataObject dataObject;

    /**
     * Instantiate this request with an id and data object.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key and the data object an Item or Row.
     * @param id the document id
     * @param deleted Whether the object was deleted. Use {@code false} if the object was not found.
     */
    public GetDataObjectResponse(String id, DataObject dataObject) {
        this.id = id;
        this.dataObject = dataObject;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return this.id;
    }

    /**
     * Returns the data object
     * @return the data object
     */
    public DataObject dataObject() {
        return this.dataObject;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private DataObject dataObject = null;

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
         * Add a data object to this builder
         * @param dataObject the data object
         * @return the updated builder
         */
        public Builder dataObject(DataObject dataObject) {
            this.dataObject = dataObject;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link GetDataObjectResponse}
         */
        public GetDataObjectResponse build() {
            return new GetDataObjectResponse(this.id, this.dataObject);
        }
    }
}
