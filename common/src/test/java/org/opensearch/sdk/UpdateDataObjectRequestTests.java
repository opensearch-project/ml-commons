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
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.ToXContentObject;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UpdateDataObjectRequestTests {

    private String testIndex;
    private String testId;
    private String testTenantId;
    private ToXContentObject testDataObject;
    private Map<String, Object> testDataObjectMap;

    @Before
    public void setUp() {
        testIndex = "test-index";
        testId = "test-id";
        testTenantId = "test-tenant-id";
        testDataObject = mock(ToXContentObject.class);
        testDataObjectMap = Map.of("foo", "bar");
    }

    @Test
    public void testUpdateDataObjectRequest() {
        UpdateDataObjectRequest request = UpdateDataObjectRequest.builder().index(testIndex).id(testId).tenantId(testTenantId).dataObject(testDataObject).build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
        assertEquals(testDataObject, request.dataObject());
    }

    @Test
    public void testUpdateDataObjectMapRequest() {
        UpdateDataObjectRequest request = UpdateDataObjectRequest.builder().index(testIndex).id(testId).tenantId(testTenantId).dataObject(testDataObjectMap).build();

        assertEquals(testIndex, request.index());
        assertEquals(testId, request.id());
        assertEquals(testTenantId, request.tenantId());
        assertEquals(testDataObjectMap, XContentHelper.convertToMap(JsonXContent.jsonXContent, Strings.toString(XContentType.JSON, request.dataObject()), false));        
    }
}
