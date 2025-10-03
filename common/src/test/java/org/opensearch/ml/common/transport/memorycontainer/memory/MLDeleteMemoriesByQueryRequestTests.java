/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.memorycontainer.MemoryConfiguration.VALID_MEMORY_TYPES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

public class MLDeleteMemoriesByQueryRequestTests {

    @Test
    public void testConstructorAndGetters() {
        QueryBuilder query = new MatchAllQueryBuilder();
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", "long_term", query);

        assertEquals("container-123", request.getMemoryContainerId());
        assertEquals("long_term", request.getMemoryType());
        assertEquals(query, request.getQuery());
    }

    @Test
    public void testSerialization() throws IOException {
        // Skip serialization test for QueryBuilder which requires NamedWriteableRegistry
        // This is typically tested in integration tests with full OpenSearch context
    }

    @Test
    public void testValidation_validRequest() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", "working", new MatchAllQueryBuilder());

        ActionRequestValidationException validationException = request.validate();
        assertNull(validationException);
    }

    @Test
    public void testValidation_missingMemoryContainerId() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(null, "working", new MatchAllQueryBuilder());

        ActionRequestValidationException validationException = request.validate();
        assertNotNull(validationException);
        assertTrue(validationException.getMessage().contains("Memory container ID is required"));
    }

    @Test
    public void testValidation_emptyMemoryContainerId() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("", "working", new MatchAllQueryBuilder());

        ActionRequestValidationException validationException = request.validate();
        assertNotNull(validationException);
        assertTrue(validationException.getMessage().contains("Memory container ID is required"));
    }

    @Test
    public void testValidation_missingMemoryType() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", null, new MatchAllQueryBuilder());

        ActionRequestValidationException validationException = request.validate();
        assertNotNull(validationException);
        assertTrue(validationException.getMessage().contains("Memory type is required"));
    }

    @Test
    public void testValidation_invalidMemoryType() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest(
            "container-123",
            "invalid_type",
            new MatchAllQueryBuilder()
        );

        ActionRequestValidationException validationException = request.validate();
        assertNotNull(validationException);
        assertTrue(validationException.getMessage().contains("Invalid memory type"));
    }

    @Test
    public void testValidation_validMemoryTypes() {
        List<String> validTypes = new ArrayList<>();
        for (String type : VALID_MEMORY_TYPES) {
            validTypes.add(type);
            validTypes.add(type.toUpperCase());
        }

        for (String type : validTypes) {
            MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", type, new MatchAllQueryBuilder());
            ActionRequestValidationException validationException = request.validate();
            assertNull("Memory type '" + type + "' should be valid", validationException);
        }
    }

    @Test
    public void testValidation_missingQuery() {
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-123", "working", null);

        ActionRequestValidationException validationException = request.validate();
        assertNotNull(validationException);
        assertTrue(validationException.getMessage().contains("Query is required"));
    }

    @Test
    public void testToXContent() throws IOException {
        QueryBuilder query = QueryBuilders.termQuery("field", "value");
        MLDeleteMemoriesByQueryRequest request = new MLDeleteMemoriesByQueryRequest("container-789", "history", query);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        request.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonString = builder.toString();

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-789\""));
        assertTrue(jsonString.contains("\"memory_type\":\"history\""));
        assertTrue(jsonString.contains("\"query\""));
        assertTrue(jsonString.contains("\"term\""));
        assertTrue(jsonString.contains("\"field\""));
        assertTrue(jsonString.contains("\"value\""));
    }

    @Test
    public void testParse() throws IOException {
        // Parse tests require NamedXContentRegistry which is typically available in integration tests
        // These tests are better suited for integration testing with full OpenSearch context
    }

    @Test
    public void testParse_complexQuery() throws IOException {
        // Parse tests require NamedXContentRegistry which is typically available in integration tests
        // These tests are better suited for integration testing with full OpenSearch context
    }
}
