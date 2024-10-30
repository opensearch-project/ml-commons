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

public class PutDataObjectResponse extends DataObjectResponse {

    /**
     * Instantiate this request with an id and parser representing an IndexResponse
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser a parser that can be used to create an IndexResponse
     * @param failed whether the request failed
     */
    public PutDataObjectResponse(String id, XContentParser parser, boolean failed) {
        super(id, parser, failed);
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

        /**
         * Builds the response
         * @return A {@link PutDataObjectResponse}
         */
        public PutDataObjectResponse build() {
            return new PutDataObjectResponse(this.id, this.parser, this.failed);
        }
    }
}
