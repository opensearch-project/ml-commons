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

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BulkDataObjectRequestTests {
    private String testIndex;
    private String testGlobalIndex;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testGlobalIndex = "test-global-index";
    }

    @Test
    public void testBulkDataObjectRequest() {
        BulkDataObjectRequest request = new BulkDataObjectRequest(testGlobalIndex)
            .add(PutDataObjectRequest.builder().index(testIndex).build())
            .add(UpdateDataObjectRequest.builder().build())
            .add(DeleteDataObjectRequest.builder().index(testIndex).build());

        assertEquals(Set.of(testIndex, testGlobalIndex), request.getIndices());
        assertEquals(3, request.requests().size());

        DataObjectRequest r0 = request.requests().get(0);
        assertTrue(r0 instanceof PutDataObjectRequest);
        assertEquals(testIndex, r0.index());

        DataObjectRequest r1 = request.requests().get(1);
        assertTrue(r1 instanceof UpdateDataObjectRequest);
        assertEquals(testGlobalIndex, r1.index());

        DataObjectRequest r2 = request.requests().get(2);
        assertTrue(r2 instanceof DeleteDataObjectRequest);
        assertEquals(testIndex, r2.index());
    }

    @Test
    public void testBulkDataObjectRequest_Exceptions() {
        PutDataObjectRequest nullIndexRequest = PutDataObjectRequest.builder().build();
        GetDataObjectRequest badTypeRequest = GetDataObjectRequest.builder().index(testIndex).build();

        BulkDataObjectRequest bulkRequest = new BulkDataObjectRequest();
        assertThrows(IllegalArgumentException.class, () -> bulkRequest.add(nullIndexRequest));
        assertThrows(IllegalArgumentException.class, () -> bulkRequest.add(badTypeRequest));
    }
}
