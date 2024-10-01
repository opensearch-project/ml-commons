/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.util.Map;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class IndexUtils {

    /**
     * Default settings for index creation with a single shard and one replica.
     * - Sets the number of shards to 1 for better performance in small indices.
     * - Uses auto-expand replicas (0-1) to ensure high availability while minimizing resource usage.
     */
    public static final Map<String, Object> DEFAULT_INDEX_SETTINGS = Map
        .of("index.number_of_shards", "1", "index.auto_expand_replicas", "0-1");
    /**
     * Default settings for index creation with replicas on all nodes.
     * - Sets the number of shards to 1 for better performance in small indices.
     * - Uses auto-expand replicas (0-all) to ensure a replica on every node, maximizing availability.
     * - Caution: This can significantly increase storage requirements and indexing load.
     * - Suitable for small, critical indices where maximum redundancy is required.
     */
    public static final Map<String, Object> ALL_NODES_REPLICA_INDEX_SETTINGS = Map
        .of("index.number_of_shards", "1", "index.auto_expand_replicas", "0-all");

    // Note: This does not include static settings like number of shards, which can't be changed after index creation.
    public static final Map<String, Object> UPDATED_DEFAULT_INDEX_SETTINGS = Map.of("index.auto_expand_replicas", "0-1");
    public static final Map<String, Object> UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS = Map.of("index.auto_expand_replicas", "0-all");
}
