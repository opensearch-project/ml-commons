/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.Collections;
import java.util.Map;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;

public class GetDataObjectResponse extends DataObjectResponse {
    private final Map<String, Object> source;

    /**
     * Instantiate this request with an id and parser/map used to recreate the data object.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param index the index
     * @param id the document id
     * @param parser a parser that can be used to create a GetResponse
     * @param failed whether the request failed
     * @param cause the Exception causing the failure
     * @param status the RestStatus
     * @param source the data object as a map
     */
    public GetDataObjectResponse(String index, String id, XContentParser parser, boolean failed, Exception cause, RestStatus status, Map<String, Object> source) {
        super(index, id, parser, failed, cause, status);
        this.source = source;
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
    public static class Builder extends DataObjectResponse.Builder<Builder> {
        private Map<String, Object> source = Collections.emptyMap();

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
            return new GetDataObjectResponse(this.index, this.id, this.parser, this.failed, this.cause, this.status, this.source);
        }
    }
}
