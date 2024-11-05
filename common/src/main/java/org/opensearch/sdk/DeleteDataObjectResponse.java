/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;

public class DeleteDataObjectResponse extends DataObjectResponse {

    /**
     * Instantiate this request with an id and parser representing a DeleteResponse
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param index the index
     * @param id the document id
     * @param parser a parser that can be used to create a DeleteResponse
     * @param failed whether the request failed
     * @param cause the Exception causing the failure
     * @param status the RestStatus
     */
    public DeleteDataObjectResponse(String index, String id, XContentParser parser, boolean failed, Exception cause, RestStatus status) {
        super(index, id, parser, failed, cause, status);
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
         * @return A {@link DeleteDataObjectResponse}
         */
        public DeleteDataObjectResponse build() {
            return new DeleteDataObjectResponse(this.index, this.id, this.parser, this.failed, this.cause, this.status);
        }
    }
}
