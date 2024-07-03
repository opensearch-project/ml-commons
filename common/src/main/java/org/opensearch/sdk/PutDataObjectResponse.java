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

public class PutDataObjectResponse {
    private final String id;
    private final XContentParser parser;

    /**
     * Instantiate this request with an id and parser representing an IndexResponse
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser a parser that can be used to create an IndexResponse
     */
    public PutDataObjectResponse(String id, XContentParser parser) {
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
     * Returns the parser that can be used to create an IndexResponse
     * @return the parser
     */
    public XContentParser parser() {
        return this.parser;
    }
    
    /**
     * Instantiate a builder for this object
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
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
        private Builder() {}

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
         * @param parser a parser that can be used to create an IndexResponse
         * @return the updated builder
         */
        public Builder parser(XContentParser parser) {
            this.parser = parser;
            return this;
        }
        
        /**
         * Builds the response
         * @return A {@link PutDataObjectResponse}
         */
        public PutDataObjectResponse build() {
            return new PutDataObjectResponse(this.id, this.parser);
        }
    }
}
