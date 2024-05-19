/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.core.common.Strings;
import org.opensearch.core.index.shard.ShardId;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class DeleteDataObjectResponseTests {
    private String testId;
    private String testIndex;
    private ShardId testShardId;
    private ShardInfo testShardInfo;
    private boolean testDeleted;

    @Before
    public void setUp() {
        testId = "test-id";
        testIndex = "test-index";
        testShardId = new ShardId("test-index", Strings.UNKNOWN_UUID_VALUE, 0);
        testShardInfo = mock(ShardInfo.class);
        testDeleted = true;
    }

    @Test
    public void testDeleteDataObjectResponse() {
        DeleteDataObjectResponse response = new DeleteDataObjectResponse.Builder()
            .id(testId)
            .shardId(testShardId)
            .shardInfo(testShardInfo)
            .deleted(testDeleted)
            .build();

        assertEquals(testId, response.id());
        assertEquals(testShardId, response.shardId());
        assertEquals(testShardInfo, response.shardInfo());
        assertEquals(testDeleted, response.deleted());
    }

    @Test
    public void testDeleteDataObjectResponseWithIndexName() {
        DeleteDataObjectResponse response = new DeleteDataObjectResponse.Builder().id(testId).shardId(testIndex).shardInfo(testShardInfo).deleted(testDeleted).build();

        assertEquals(testId, response.id());
        assertEquals(testShardId, response.shardId());
        assertEquals(testShardInfo, response.shardInfo());
        assertEquals(testDeleted, response.deleted());
    }
}
