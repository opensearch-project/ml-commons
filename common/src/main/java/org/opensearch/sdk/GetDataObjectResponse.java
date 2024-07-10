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

import java.util.Collections;
import java.util.Map;

public class GetDataObjectResponse {
    private final String id;
    private final XContentParser parser;
    private final Map<String, Object> source;

    /**
     * Instantiate this request with an id and parser/map used to recreate the data object.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser a parser that can be used to create a GetResponse
     * @param source the data object as a map
     */
    public GetDataObjectResponse(String id, XContentParser parser, Map<String, Object> source) {
        this.id = id;
        this.parser = parser;
        this.source = source;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return this.id;
    }
    
    /**
     * Returns the parser that can be used to create a GetResponse
     * @return the parser
     */
    public XContentParser parser() {
        return this.parser;
    }
    
    /**
     * Returns the source map. This is a logical representation of the data object.
     * @return the source map
     */
    public Map<String, Object> source() {
        return this.source;
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
        private Map<String, Object> source = Collections.emptyMap();

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
         * @param parser a parser that can be used to create a GetResponse
         * @return the updated builder
         */
        public Builder parser(XContentParser parser) {
            this.parser = parser;
            return this;
        }

        /**
         * Add a source map to this builder
         * @param source the data object as a map
         * @return the updated builder
         */
        public Builder source(Map<String, Object> source) {
            this.source = source == null ? Collections.emptyMap() : source;
            return this;
        }
        
        /**
         * Builds the response
         * @return A {@link GetDataObjectResponse}
         */
        public GetDataObjectResponse build() {
            return new GetDataObjectResponse(this.id, this.parser, this.source);
        }
    }
}
