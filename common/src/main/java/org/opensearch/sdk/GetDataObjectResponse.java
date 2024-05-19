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

import java.util.Optional;

public class GetDataObjectResponse {
    private final String id;
    private final Optional<XContentParser> parser;

    /**
     * Instantiate this request with an id and parser used to recreate the data object.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser an optional XContentParser that can be used to create the object if present.
     */
    public GetDataObjectResponse(String id, Optional<XContentParser> parser) {
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
     * Returns the parser optional
     * @return the parser optional
     */
    public Optional<XContentParser> parser() {
        return this.parser;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private Optional<XContentParser> parser = Optional.empty();

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
         * @param parser an {@link Optional} which may contain the parser
         * @return the updated builder
         */
        public Builder parser(Optional<XContentParser> parser) {
            this.parser = parser;
            return this;
        }

        /**
         * Builds the response
         * @return A {@link GetDataObjectResponse}
         */
        public GetDataObjectResponse build() {
            return new GetDataObjectResponse(this.id, this.parser);
        }
    }
}
