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

public abstract class DataObjectResponse {
    private final String id;
    private final XContentParser parser;
    private final boolean failed;

    /**
     * Instantiate this request with an id and parser representing a Response
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param parser a parser that can be used to create a Response
     * @param failed whether the request failed
     */
    protected DataObjectResponse(String id, XContentParser parser, boolean failed) {
        this.id = id;
        this.parser = parser;
        this.failed = failed;
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
     * Superclass for common fields in subclass builders
     */
    public static class Builder<T extends Builder<T>> {
        protected String id = null;
        protected XContentParser parser;
        protected boolean failed = false;

        /**
         * Empty constructor to initialize
         */
        protected Builder() {}

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
         * Returns this builder as the parameterized type.
         */
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
    }
}
