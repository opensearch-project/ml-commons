/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MemoryDecisionRequestTest {

    private MemoryDecisionRequest requestWithAllFields;
    private MemoryDecisionRequest requestMinimal;
    private MemoryDecisionRequest requestEmpty;
    private List<MemoryDecisionRequest.OldMemory> testOldMemories;
    private List<String> testRetrievedFacts;

    @Before
    public void setUp() {
        // Create test old memories
        testOldMemories = Arrays
            .asList(
                MemoryDecisionRequest.OldMemory.builder().id("mem-1").text("User's name is John").score(0.95f).build(),
                MemoryDecisionRequest.OldMemory.builder().id("mem-2").text("Lives in Boston").score(0.87f).build(),
                MemoryDecisionRequest.OldMemory.builder().id("mem-3").text("Works at TechCorp").score(0.76f).build()
            );

        // Create test retrieved facts
        testRetrievedFacts = Arrays
            .asList("User's name is John", "Lives in San Francisco", "Works at TechCorp", "Has 10 years of experience");

        // Request with all fields
        requestWithAllFields = MemoryDecisionRequest.builder().oldMemory(testOldMemories).retrievedFacts(testRetrievedFacts).build();

        // Minimal request (only retrieved facts)
        requestMinimal = MemoryDecisionRequest.builder().retrievedFacts(Arrays.asList("Single fact")).build();

        // Empty request
        requestEmpty = MemoryDecisionRequest.builder().oldMemory(new ArrayList<>()).retrievedFacts(new ArrayList<>()).build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(requestWithAllFields);
        assertEquals(testOldMemories, requestWithAllFields.getOldMemory());
        assertEquals(testRetrievedFacts, requestWithAllFields.getRetrievedFacts());
        assertEquals(3, requestWithAllFields.getOldMemory().size());
        assertEquals(4, requestWithAllFields.getRetrievedFacts().size());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(requestMinimal);
        assertEquals(null, requestMinimal.getOldMemory());
        assertEquals(1, requestMinimal.getRetrievedFacts().size());
        assertEquals("Single fact", requestMinimal.getRetrievedFacts().get(0));
    }

    @Test
    public void testBuilderEmpty() {
        assertNotNull(requestEmpty);
        assertEquals(0, requestEmpty.getOldMemory().size());
        assertEquals(0, requestEmpty.getRetrievedFacts().size());
    }

    @Test
    public void testOldMemoryBuilder() {
        MemoryDecisionRequest.OldMemory oldMemory = MemoryDecisionRequest.OldMemory
            .builder()
            .id("test-id")
            .text("test memory text")
            .score(0.89f)
            .build();

        assertEquals("test-id", oldMemory.getId());
        assertEquals("test memory text", oldMemory.getText());
        assertEquals(0.89f, oldMemory.getScore(), 0.001);
    }

    @Test
    public void testOldMemoryToXContent() throws IOException {
        MemoryDecisionRequest.OldMemory oldMemory = testOldMemories.get(0);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        oldMemory.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"id\":\"mem-1\""));
        assertTrue(jsonString.contains("\"text\":\"User's name is John\""));
        assertTrue(jsonString.contains("\"score\":0.95"));
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        requestWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Check structure
        assertTrue(jsonString.contains("\"old_memory\":["));
        assertTrue(jsonString.contains("\"retrieved_facts\":["));

        // Check old memories
        assertTrue(jsonString.contains("\"id\":\"mem-1\""));
        assertTrue(jsonString.contains("\"text\":\"User's name is John\""));
        assertTrue(jsonString.contains("\"score\":0.95"));
        assertTrue(jsonString.contains("\"id\":\"mem-2\""));
        assertTrue(jsonString.contains("\"text\":\"Lives in Boston\""));
        assertTrue(jsonString.contains("\"score\":0.87"));

        // Check retrieved facts
        assertTrue(jsonString.contains("\"Lives in San Francisco\""));
        assertTrue(jsonString.contains("\"Has 10 years of experience\""));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        requestMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Should have empty old_memory array
        assertTrue(jsonString.contains("\"old_memory\":[]"));
        // Should have retrieved_facts
        assertTrue(jsonString.contains("\"retrieved_facts\":[\"Single fact\"]"));
    }

    @Test
    public void testToXContentEmpty() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        requestEmpty.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Both arrays should be empty
        assertTrue(jsonString.contains("\"old_memory\":[]"));
        assertTrue(jsonString.contains("\"retrieved_facts\":[]"));
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        MemoryDecisionRequest requestNulls = MemoryDecisionRequest.builder().oldMemory(null).retrievedFacts(null).build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        requestNulls.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Should have empty arrays
        assertTrue(jsonString.contains("\"old_memory\":[]"));
        assertTrue(jsonString.contains("\"retrieved_facts\":[]"));
    }

    @Test
    public void testToJsonString() {
        String jsonString = requestWithAllFields.toJsonString();
        assertNotNull(jsonString);

        // Verify JSON structure
        assertTrue(jsonString.contains("\"old_memory\":["));
        assertTrue(jsonString.contains("\"retrieved_facts\":["));
        assertTrue(jsonString.contains("\"id\":\"mem-1\""));
        assertTrue(jsonString.contains("\"Lives in San Francisco\""));
    }

    @Test
    public void testToJsonStringMinimal() {
        String jsonString = requestMinimal.toJsonString();
        assertNotNull(jsonString);

        assertTrue(jsonString.contains("\"old_memory\":[]"));
        assertTrue(jsonString.contains("\"retrieved_facts\":[\"Single fact\"]"));
    }

    @Test
    public void testDataAnnotationMethods() {
        // Test @Data generated methods
        MemoryDecisionRequest request1 = MemoryDecisionRequest
            .builder()
            .oldMemory(testOldMemories)
            .retrievedFacts(testRetrievedFacts)
            .build();

        MemoryDecisionRequest request2 = MemoryDecisionRequest
            .builder()
            .oldMemory(testOldMemories)
            .retrievedFacts(testRetrievedFacts)
            .build();

        // Test equals
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        // Test setters
        List<String> newFacts = Arrays.asList("New fact 1", "New fact 2");
        request1.setRetrievedFacts(newFacts);
        assertEquals(newFacts, request1.getRetrievedFacts());

        // Test toString
        String str = request1.toString();
        assertTrue(str.contains("oldMemory"));
        assertTrue(str.contains("retrievedFacts"));
    }

    @Test
    public void testOldMemoryDataAnnotations() {
        MemoryDecisionRequest.OldMemory memory1 = MemoryDecisionRequest.OldMemory.builder().id("id-1").text("text-1").score(0.9f).build();

        MemoryDecisionRequest.OldMemory memory2 = MemoryDecisionRequest.OldMemory.builder().id("id-1").text("text-1").score(0.9f).build();

        // Test equals
        assertEquals(memory1, memory2);
        assertEquals(memory1.hashCode(), memory2.hashCode());

        // Test setters
        memory1.setId("new-id");
        memory1.setText("new-text");
        memory1.setScore(0.5f);

        assertEquals("new-id", memory1.getId());
        assertEquals("new-text", memory1.getText());
        assertEquals(0.5f, memory1.getScore(), 0.001);
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        List<MemoryDecisionRequest.OldMemory> specialMemories = Arrays
            .asList(
                MemoryDecisionRequest.OldMemory
                    .builder()
                    .id("id-with-special-🔥")
                    .text("Text with\n\ttabs and \"quotes\"")
                    .score(0.99f)
                    .build()
            );

        List<String> specialFacts = Arrays.asList("Fact with 'quotes'", "Fact with\nnewlines", "Fact with unicode ✨");

        MemoryDecisionRequest specialRequest = MemoryDecisionRequest
            .builder()
            .oldMemory(specialMemories)
            .retrievedFacts(specialFacts)
            .build();

        String jsonString = specialRequest.toJsonString();
        assertNotNull(jsonString);

        // Verify special characters are properly handled - JSON may escape unicode
        assertTrue(jsonString.contains("id-with-special-"));
        // JSON escaping will handle newlines and tabs
        assertTrue(jsonString.contains("Text with"));
        assertTrue(jsonString.contains("tabs"));
        assertTrue(jsonString.contains("quotes"));
        assertTrue(jsonString.contains("Fact with unicode"));
    }

    @Test
    public void testLargeRequest() throws IOException {
        // Test with many items
        List<MemoryDecisionRequest.OldMemory> manyMemories = new ArrayList<>();
        List<String> manyFacts = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            manyMemories.add(MemoryDecisionRequest.OldMemory.builder().id("mem-" + i).text("Memory text " + i).score(i / 100.0f).build());
            manyFacts.add("Fact number " + i);
        }

        MemoryDecisionRequest largeRequest = MemoryDecisionRequest.builder().oldMemory(manyMemories).retrievedFacts(manyFacts).build();

        String jsonString = largeRequest.toJsonString();
        assertNotNull(jsonString);

        assertEquals(100, largeRequest.getOldMemory().size());
        assertEquals(100, largeRequest.getRetrievedFacts().size());
    }
}
