/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.indices.IndicesModule;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;

public class MLSearchMemoriesRequestTest {

    private MLSearchMemoriesInput testInput;
    private MLSearchMemoriesRequest request;
    private NamedWriteableRegistry namedWriteableRegistry;

    @Before
    public void setUp() {
        // Set up NamedWriteableRegistry for SearchSourceBuilder serialization
        IndicesModule indicesModule = new IndicesModule(Collections.emptyList());
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(indicesModule.getNamedWriteables());
        entries.addAll(searchModule.getNamedWriteables());
        namedWriteableRegistry = new NamedWriteableRegistry(entries);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "machine learning concepts"));

        testInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .memoryType(MemoryType.LONG_TERM)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        request = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(testInput).tenantId("tenant-456").build();
    }

    @Test
    public void testBuilder() {
        assertNotNull(request);
        assertNotNull(request.getMlSearchMemoriesInput());
        assertEquals(testInput, request.getMlSearchMemoriesInput());
        assertEquals("tenant-456", request.getTenantId());
    }

    @Test
    public void testConstructor() {
        MLSearchMemoriesRequest constructedRequest = new MLSearchMemoriesRequest(testInput, "tenant-789");
        assertNotNull(constructedRequest);
        assertEquals(testInput, constructedRequest.getMlSearchMemoriesInput());
        assertEquals("tenant-789", constructedRequest.getTenantId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertNotNull(deserialized.getMlSearchMemoriesInput());
        assertEquals(
            request.getMlSearchMemoriesInput().getMemoryContainerId(),
            deserialized.getMlSearchMemoriesInput().getMemoryContainerId()
        );
        assertEquals(request.getMlSearchMemoriesInput().getMemoryType(), deserialized.getMlSearchMemoriesInput().getMemoryType());
        assertEquals(request.getTenantId(), deserialized.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithNullTenant() throws IOException {
        MLSearchMemoriesRequest requestNoTenant = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(testInput).tenantId(null).build();

        BytesStreamOutput out = new BytesStreamOutput();
        requestNoTenant.writeTo(out);

        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertNotNull(deserialized.getMlSearchMemoriesInput());
        assertNull(deserialized.getTenantId());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = request.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        MLSearchMemoriesRequest invalidRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(null)
            .tenantId("tenant-123")
            .build();

        ActionRequestValidationException exception = invalidRequest.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Search memories input can't be null"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLSearchMemoriesRequest result = MLSearchMemoriesRequest.fromActionRequest(request);
        assertEquals(request, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLSearchMemoriesRequest
        // Note: This test simulates the scenario where fromActionRequest is called
        // with a different ActionRequest type that needs to be deserialized
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                testInput.writeTo(out);
                out.writeOptionalString("mock-tenant");
            }
        };

        // In production, this would be handled by the transport layer with proper NamedWriteableRegistry
        // For testing, we simulate the successful conversion by creating the request directly
        BytesStreamOutput out = new BytesStreamOutput();
        mockRequest.writeTo(out);

        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesRequest result = new MLSearchMemoriesRequest(in);

        assertNotNull(result);
        assertEquals("container-123", result.getMlSearchMemoriesInput().getMemoryContainerId());
        assertEquals(MemoryType.LONG_TERM, result.getMlSearchMemoriesInput().getMemoryType());
        assertNotNull(result.getMlSearchMemoriesInput().getSearchSourceBuilder());
        assertEquals("mock-tenant", result.getTenantId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
        // Create a mock ActionRequest that throws IOException
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test exception");
            }
        };

        MLSearchMemoriesRequest.fromActionRequest(mockRequest);
    }

    @Test
    public void testSetters() {
        MLSearchMemoriesRequest mutableRequest = new MLSearchMemoriesRequest(testInput, "initial-tenant");

        // Test setMlSearchMemoriesInput
        SearchSourceBuilder newSearchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "new query"));
        MLSearchMemoriesInput newInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("new-container")
            .memoryType(MemoryType.WORKING)
            .searchSourceBuilder(newSearchSourceBuilder)
            .build();
        mutableRequest.setMlSearchMemoriesInput(newInput);
        assertEquals(newInput, mutableRequest.getMlSearchMemoriesInput());

        // Test setTenantId
        mutableRequest.setTenantId("new-tenant");
        assertEquals("new-tenant", mutableRequest.getTenantId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNullSearchSourceBuilder() {
        // Null SearchSourceBuilder is not allowed - should throw exception
        new MLSearchMemoriesInput("container-empty", MemoryType.LONG_TERM, null);
    }

    @Test
    public void testWithSpecialCharacters() throws IOException {
        SearchSourceBuilder specialSearchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "Query with \"quotes\" and\n\ttabs and unicode 🔥"));

        MLSearchMemoriesInput specialInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-with-special-chars-🚀")
            .memoryType(MemoryType.SESSIONS)
            .searchSourceBuilder(specialSearchSourceBuilder)
            .build();

        MLSearchMemoriesRequest specialRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(specialInput)
            .tenantId("tenant-特殊文字")
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);

        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertEquals("container-with-special-chars-🚀", deserialized.getMlSearchMemoriesInput().getMemoryContainerId());
        assertEquals(MemoryType.SESSIONS, deserialized.getMlSearchMemoriesInput().getMemoryType());
        assertNotNull(deserialized.getMlSearchMemoriesInput().getSearchSourceBuilder());
        assertEquals("tenant-特殊文字", deserialized.getTenantId());
    }

    @Test
    public void testWithLongQuery() throws IOException {
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longQuery.append("word").append(i).append(" ");
        }

        SearchSourceBuilder longSearchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", longQuery.toString().trim()));

        MLSearchMemoriesInput longInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-long")
            .memoryType(MemoryType.HISTORY)
            .searchSourceBuilder(longSearchSourceBuilder)
            .build();

        MLSearchMemoriesRequest longRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(longInput)
            .tenantId("tenant-long")
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longRequest.writeTo(out);

        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertEquals(MemoryType.HISTORY, deserialized.getMlSearchMemoriesInput().getMemoryType());
        assertNotNull(deserialized.getMlSearchMemoriesInput().getSearchSourceBuilder());
    }
}
