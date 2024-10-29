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
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.sdk.PutDataObjectRequest.Builder;

import java.io.IOException;
import java.util.Map;

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

    @Test
    public void testPutDataObjectRequestWithMap() throws IOException {
        Map<String, Object> dataObjectMap = Map.of("key1", "value1", "key2", "value2");

        Builder builder = PutDataObjectRequest.builder().index(testIndex).id(testId).tenantId(testTenantId).dataObject(dataObjectMap);
        PutDataObjectRequest request = builder.build();

        // Verify the index, id, tenantId, and overwriteIfExists fields
        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
        assertTrue(request.overwriteIfExists());

        // Verify the dataObject field by converting it back to a Map and comparing
        ToXContentObject dataObject = request.dataObject();
        XContentBuilder contentBuilder = XContentFactory.jsonBuilder();
        dataObject.toXContent(contentBuilder, ToXContent.EMPTY_PARAMS);
        contentBuilder.flush();

        BytesReference bytes = BytesReference.bytes(contentBuilder);
        Map<String, Object> resultingMap = XContentHelper.convertToMap(bytes, false, (MediaType) XContentType.JSON).v2();

        assertEquals(dataObjectMap, resultingMap);
    }

}
