/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.core.xcontent.XContentParser;

public class UpdateDataObjectResponse {
    private final String id;
    private final XContentParser parser;

    /**
     * Instantiate this request with an id and parser representing an UpdateResponse
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser a parser that can be used to create an UpdateResponse
     */
    public UpdateDataObjectResponse(String id, XContentParser parser) {
        this.id = id;
        this.parser = parser;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return this.id;
    }
    
    /**
     * Returns the parser that can be used to create an UpdateResponse
     * @return the parser
     */
    public XContentParser parser() {
        return this.parser;
    }
    
    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private XContentParser parser = null;

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
         * Add a parser to this builder
         * @param parser a parser that can be used to create an UpdateResponse
         * @return the updated builder
         */
        public Builder parser(XContentParser parser) {
            this.parser = parser;
            return this;
        }
        
        /**
         * Builds the response
         * @return A {@link UpdateDataObjectResponse}
         */
        public UpdateDataObjectResponse build() {
            return new UpdateDataObjectResponse(this.id, this.parser);
        }
    }
}
