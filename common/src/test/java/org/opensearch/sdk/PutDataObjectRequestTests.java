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
import org.opensearch.core.xcontent.ToXContentObject;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PutDataObjectRequestTests {

    private String testIndex;
    private String testTenantId;
    private ToXContentObject testDataObject;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testTenantId = "test-tenant-id";
        testDataObject = mock(ToXContentObject.class);
    }

    @Test
    public void testPutDataObjectRequest() {
        PutDataObjectRequest request = new PutDataObjectRequest.Builder().index(testIndex).tenantId(testTenantId).dataObject(testDataObject).build();

        assertEquals(testIndex, request.index());
        assertEquals(testTenantId, request.tenantId());
        assertEquals(testDataObject, request.dataObject());
    }
}
