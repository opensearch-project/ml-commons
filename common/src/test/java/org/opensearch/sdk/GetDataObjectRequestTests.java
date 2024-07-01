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
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class GetDataObjectRequestTests {

    private String testIndex;
    private String testId;
    private String testTenantId;    
    private FetchSourceContext testFetchSourceContext;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testId = "test-id";
        testTenantId = "test-tenant-id";
        testFetchSourceContext = mock(FetchSourceContext.class);
    }

    @Test
    public void testGetDataObjectRequest() {
        GetDataObjectRequest request = new GetDataObjectRequest.Builder()
            .index(testIndex)
            .id(testId)
            .tenantId(testTenantId)
            .fetchSourceContext(testFetchSourceContext)
            .build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
        assertEquals(testFetchSourceContext, request.fetchSourceContext());
    }
}
