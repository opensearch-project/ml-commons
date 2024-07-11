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
import org.opensearch.sdk.PutDataObjectRequest.Builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PutDataObjectRequestTests {

    private String testIndex;
    private String testId;
    private String testTenantId;
    private ToXContentObject testDataObject;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testId = "test-id";
        testTenantId = "test-tenant-id";
        testDataObject = mock(ToXContentObject.class);
    }

    @Test
    public void testPutDataObjectRequest() {
        Builder builder = PutDataObjectRequest.builder().index(testIndex).id(testId).tenantId(testTenantId).dataObject(testDataObject);
        PutDataObjectRequest request = builder.build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
        assertTrue(request.overwriteIfExists());
        assertSame(testDataObject, request.dataObject());
        
        builder.overwriteIfExists(false);
        request = builder.build();
        assertFalse(request.overwriteIfExists());
    }
}
