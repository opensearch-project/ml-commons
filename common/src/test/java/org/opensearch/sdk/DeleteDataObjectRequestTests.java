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

import static org.junit.Assert.assertEquals;

public class DeleteDataObjectRequestTests {
    private String testIndex;
    private String testId;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testId = "test-id";
    }

    public void testDeleteDataObjectRequest() {
        DeleteDataObjectRequest request = new DeleteDataObjectRequest.Builder().index(testIndex).id(testId).build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
    }
}
