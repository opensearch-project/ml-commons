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

import static org.junit.Assert.assertEquals;

public class DeleteDataObjectRequestTests {
    private String testIndex;
    private String testId;
    private String testTenantId;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testId = "test-id";
        testTenantId = "test-tenant-id";
    }

    @Test
    public void testDeleteDataObjectRequest() {
        DeleteDataObjectRequest request = DeleteDataObjectRequest.builder().index(testIndex).id(testId).tenantId(testTenantId).build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
    }
}
