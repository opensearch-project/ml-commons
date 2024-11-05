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

public abstract class DataObjectResponse {
    private final String index;
    private final String id;
    private final XContentParser parser;
    private final boolean failed;
    private final Exception cause;
    private final RestStatus status;

    /**
     * Instantiate this request with an index, id, failure status, and parser representing a Response
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param index the index
     * @param id the document id
     * @param parser a parser that can be used to create a Response
     * @param failed whether the request failed
     * @param cause the Exception causing the failure
     * @param status the RestStatus
     */
    protected DataObjectResponse(String index, String id, XContentParser parser, boolean failed, Exception cause, RestStatus status) {
        this.index = index;
        this.id = id;
        this.parser = parser;
        this.failed = failed;
        this.cause = cause;
        this.status = status;
    }

    /**
     * Returns the index
     * @return the index
     */
    public String index() {
        return this.index;
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
     * Has anything failed with the execution.
     * @return whether the corresponding bulk request failed
     */
    public boolean isFailed() {
        return this.failed;
    }

    /**
     * The actual cause of the failure.
     * @return the Exception causing the failure
     */
    public Exception cause() {
        return this.cause;
    }

    /**
     * The rest status.
     * @return the rest status.
     */
    public RestStatus status() {
        return this.status;
    }

    /**
     * Superclass for common fields in subclass builders
     */
    public static class Builder<T extends Builder<T>> {
        protected String index = null;
        protected String id = null;
        protected XContentParser parser;
        protected boolean failed = false;
        protected Exception cause = null;
        protected RestStatus status = null;

        /**
         * Empty constructor to initialize
         */
        protected Builder() {}

        /**
         * Add an index to this builder
         * @param index the index to add
         * @return the updated builder
         */
        public T index(String index) {
            this.index = index;
            return self();
        }

        /**
         * Add an id to this builder
         * @param id the id to add
         * @return the updated builder
         */
        public T id(String id) {
            this.id = id;
            return self();
        }

        /**
         * Add a parser to this builder
         * @param parser a parser that can be used to create a Response for the subclass
         * @return the updated builder
         */
        public T parser(XContentParser parser) {
            this.parser = parser;
            return self();
        }

        /**
         * Add a failed status to this builder
         * @param failed whether the request failed
         * @return the updated builder
         */
        public T failed(boolean failed) {
            this.failed = failed;
            return self();
        }

        /**
         * Add a cause to this builder
         * @param cause the Exception
         * @return the updated builder
         */
        public T cause(Exception cause) {
            this.cause = cause;
            return self();
        }

        /**
         * Add a rest status to this builder
         * @param status the rest status
         * @return the updated builder
         */
        public T status(RestStatus status) {
            this.status = status;
            return self();
        }

        /**
         * Returns this builder as the parameterized type.
         */
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
    }
}
