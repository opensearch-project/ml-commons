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
import org.opensearch.core.common.Strings;
import org.opensearch.core.index.shard.ShardId;

import static org.junit.Assert.assertEquals;

public class DeleteDataObjectResponseTests {
    private String testId;
    private ShardId testShardId;
    private boolean testDeleted;

    @Before
    public void setUp() {
        testId = "test-id";
        testShardId = new ShardId("test-index", Strings.UNKNOWN_UUID_VALUE, 0);
        testDeleted = true;
    }

    public void testDeleteDataObjectResponse() {
        DeleteDataObjectResponse response = new DeleteDataObjectResponse.Builder()
            .id(testId)
            .shardId(testShardId)
            .deleted(testDeleted)
            .build();

        assertEquals(testId, response.id());
        assertEquals(testShardId, response.shardId());
        assertEquals(testDeleted, response.deleted());
    }

    public void testDeleteDataObjectResponseWithIndexName() {
        DeleteDataObjectResponse response = new DeleteDataObjectResponse.Builder().id(testId).shardId(testId).deleted(testDeleted).build();

        assertEquals(testId, response.id());
        assertEquals(testShardId, response.shardId());
        assertEquals(testDeleted, response.deleted());
    }

}
