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
    private String testTenantId;
    private SearchSourceBuilder testSearchSourceBuilder;

    @Before
    public void setUp() {
        testIndices = new String[] { "test-index" };
        testTenantId = "test-tenant-id";
        testSearchSourceBuilder = new SearchSourceBuilder();
    }

    @Test
    public void testGetDataObjectRequest() {
        SearchDataObjectRequest request = SearchDataObjectRequest
            .builder()
            .indices(testIndices)
            .tenantId(testTenantId)
            .searchSourceBuilder(testSearchSourceBuilder)
            .build();

        assertArrayEquals(testIndices, request.indices());
        assertEquals(testTenantId, request.tenantId());
        assertEquals(testSearchSourceBuilder, request.searchSourceBuilder());
    }
}
