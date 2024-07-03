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
import org.opensearch.core.xcontent.XContentParser;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UpdateDataObjectResponseTests {

    private String testId;
    private XContentParser testParser;

    @Before
    public void setUp() {
        testId = "test-id";
        testParser = mock(XContentParser.class);
    }

    @Test
    public void testUpdateDataObjectResponse() {
        UpdateDataObjectResponse response = UpdateDataObjectResponse.builder().id(testId).parser(testParser).build();

        assertEquals(testId, response.id());
        assertEquals(testParser, response.parser());
    }
}
