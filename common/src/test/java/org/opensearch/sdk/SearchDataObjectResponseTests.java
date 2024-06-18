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

public class SearchDataObjectResponseTests {

    private XContentParser testParser;

    @Before
    public void setUp() {
        testParser = mock(XContentParser.class);
    }

    @Test
    public void testSearchDataObjectResponse() {
        SearchDataObjectResponse response = new SearchDataObjectResponse.Builder().parser(testParser).build();

        assertEquals(testParser, response.parser());
    }
}
