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

public class PutDataObjectResponseTests {

    private String testId;
    private boolean testCreated;

    @Before
    public void setUp() {
        testId = "test-id";
        testCreated = true;
    }

    public void testPutDataObjectResponse() {
        PutDataObjectResponse response = new PutDataObjectResponse.Builder().id(testId).created(testCreated).build();

        assertEquals(testId, response.id());
        assertEquals(testCreated, response.created());
    }
}
