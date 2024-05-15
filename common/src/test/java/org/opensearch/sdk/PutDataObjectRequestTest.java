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
import org.opensearch.core.xcontent.ToXContentObject;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class PutDataObjectRequestTest {

    private String testIndex;
    private ToXContentObject testDataObject;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testDataObject = mock(ToXContentObject.class);
    }

    public void testPutDataObjectRequest() {
        PutDataObjectRequest request = new PutDataObjectRequest.Builder().index(testIndex).dataObject(testDataObject).build();

        assertEquals(testIndex, request.index());
        assertEquals(testDataObject, request.dataObject());
    }
}
