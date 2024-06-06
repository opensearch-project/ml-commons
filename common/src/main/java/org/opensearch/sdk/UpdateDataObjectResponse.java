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

public class UpdateDataObjectResponse {
    private final String id;
    private final ShardId shardId;
    private final ShardInfo shardInfo;
    private final boolean updated;

    /**
     * Instantiate this request with an id and update status.
     * <p>
     * For data storage implementations other than OpenSearch, the id may be referred to as a primary key.
     * @param id the document id
     * @param shardId the shard id
     * @param shardInfo the shard info
     * @param updated Whether the object was updated.
     */
    public UpdateDataObjectResponse(String id, ShardId shardId, ShardInfo shardInfo, boolean updated) {
        this.id = id;
        this.shardId = shardId;
        this.shardInfo = shardInfo;
        this.updated = updated;
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
     * Returns whether update was successful
     * @return true if update was successful
     */
    public boolean updated() {
        return updated;
    }

    /**
     * Class for constructing a Builder for this Response Object
     */
    public static class Builder {
        private String id = null;
        private ShardId shardId = null;
        private ShardInfo shardInfo = null;
        private boolean updated = false;

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
         * Add a updated status to this builder
         * @param updated the updated status to add
         * @return the updated builder
         */
        public Builder updated(boolean updated) {
            this.updated = updated;
            return this;
        }

        /**
         * Builds the object
         * @return A {@link UpdateDataObjectResponse}
         */
        public UpdateDataObjectResponse build() {
            return new UpdateDataObjectResponse(this.id, this.shardId, this.shardInfo, this.updated);
        }
    }
}
