/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BulkDataObjectResponseTests {
    @Test
    public void testBulkDataObjectResponse() {

        DataObjectResponse[] responses = List
            .of(
                PutDataObjectResponse.builder().build(),
                UpdateDataObjectResponse.builder().build(),
                DeleteDataObjectResponse.builder().build()
            )
            .toArray(new DataObjectResponse[0]);

        BulkDataObjectResponse response = new BulkDataObjectResponse(responses, 1L);

        assertEquals(3, response.getResponses().length);
        assertEquals(1L, response.getTookInMillis());
        assertEquals(-1L, response.getIngestTookInMillis());
        assertFalse(response.hasFailures());
    }

    @Test
    public void testBulkDataObjectRequest_Failures() {
        DataObjectResponse[] responses = List
            .of(PutDataObjectResponse.builder().build(), DeleteDataObjectResponse.builder().failed(true).build())
            .toArray(new DataObjectResponse[0]);

        BulkDataObjectResponse response = new BulkDataObjectResponse(responses, 1L);

        assertTrue(response.hasFailures());
    }
}
