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
import java.util.Optional;

public class GetDataObjectResponse {
    private final String id;
    private final Optional<XContentParser> parser;
    private final Map<String, Object> source;

    /**
     * Instantiate this request with an id and parser/map used to recreate the data object.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser an optional XContentParser that can be used to create the data object if present.
     * @param source the data object as a map
     */
    public GetDataObjectResponse(String id, Optional<XContentParser> parser, Map<String, Object> source) {
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
     * Returns the parser optional. If present, is a representation of the data object that may be parsed.
     * @return the parser optional
     */
    public Optional<XContentParser> parser() {
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
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private Optional<XContentParser> parser = Optional.empty();
        private Map<String, Object> source = Collections.emptyMap();

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
         * Add an optional parser to this builder
         * @param parser an {@link Optional} which may contain the data object parser
         * @return the updated builder
         */
        public Builder parser(Optional<XContentParser> parser) {
            this.parser = parser;
            return this;
        }

        /**
         * Add a source map to this builder
         * @param source the data object as a map
         * @return the updated builder
         */
        public Builder source(Map<String, Object> source) {
            this.source = source;
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
