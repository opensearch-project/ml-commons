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
import org.opensearch.search.builder.SearchSourceBuilder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SearchDataObjectRequestTests {

    private String[] testIndices;
    private SearchSourceBuilder testSearchSourceBuilder;

    @Before
    public void setUp() {
        testIndices = new String[] {"test-index"};
        testSearchSourceBuilder = new SearchSourceBuilder();
    }

    @Test
    public void testGetDataObjectRequest() {
        SearchDataObjectRequest request = new SearchDataObjectRequest.Builder()
            .indices(testIndices)
            .searchSourceBuilder(testSearchSourceBuilder)
            .build();

        assertArrayEquals(testIndices, request.indices());
        assertEquals(testSearchSourceBuilder, request.searchSourceBuilder());
    }
}
