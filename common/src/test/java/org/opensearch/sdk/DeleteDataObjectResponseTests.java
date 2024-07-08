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
import org.opensearch.core.xcontent.XContentParser;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class DeleteDataObjectResponseTests {

    private String testId;
    private XContentParser testParser;

    @Before
    public void setUp() {
        testId = "test-id";
        testParser = mock(XContentParser.class);
    }

    @Test
    public void testDeleteDataObjectResponse() {
        DeleteDataObjectResponse response = DeleteDataObjectResponse.builder().id(testId).parser(testParser).build();

        assertEquals(testId, response.id());
        assertEquals(testParser, response.parser());
    }
}
