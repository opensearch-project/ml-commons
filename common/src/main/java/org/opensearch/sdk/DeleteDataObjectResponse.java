/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.core.common.Strings;
import org.opensearch.core.index.shard.ShardId;

public class DeleteDataObjectResponse {
    private final String id;
    private final ShardId shardId;
    private final ShardInfo shardInfo;
    private final boolean deleted;

    /**
     * Instantiate this request.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the id may be referred to as a primary key.
     * @param id the document id
     * @param shardId the shard id
     * @param shardInfo the shard info
     * @param deleted Whether the object was deleted. Use {@code false} if the object was not found.
     */
    public DeleteDataObjectResponse(String id, ShardId shardId, ShardInfo shardInfo, boolean deleted) {
        this.id = id;
        this.shardId = shardId;
        this.shardInfo = shardInfo;
        this.deleted = deleted;
    }

    /**
     * Returns the document id
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the shard id.
     * @return the shard id, or a generated id if shards are not applicable
     */
    public ShardId shardId() {
        return shardId;
    }

    /**
     * Returns the shard info.
     * @return the shard info, or generated info if shards are not applicable
     */
    public ShardInfo shardInfo() {
        return shardInfo;
    }

    /**
     * Returns whether deletion was successful
     * @return true if deletion was successful, false if the object was not found
     */
    public boolean deleted() {
        return deleted;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private ShardId shardId = null;
        private ShardInfo shardInfo = null;
        private boolean deleted = false;

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
         * Adds a shard id to this builder
         * @param shardId the shard id to add
         * @return the updated builder
         */
        public Builder shardId(ShardId shardId) {
            this.shardId = shardId;
            return this;
        }

        /**
         * Adds a generated shard id to this builder
         * @param indexName the index name to generate a shard id
         * @return the updated builder
         */
        public Builder shardId(String indexName) {
            this.shardId = new ShardId(indexName, Strings.UNKNOWN_UUID_VALUE, 0);
            return this;
        }

        /**
         * Adds shard information (statistics) to this builder
         * @param shardInfo the shard info to add
         * @return the updated builder
         */
        public Builder shardInfo(ShardInfo shardInfo) {
            this.shardInfo = shardInfo;
            return this;
        }

        /**
         * Add a deleted status to this builder
         * @param deleted the deleted status to add
         * @return the updated builder
         */
        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link DeleteDataObjectResponse}
         */
        public DeleteDataObjectResponse build() {
            return new DeleteDataObjectResponse(this.id, this.shardId, this.shardInfo, this.deleted);
        }
    }
}
