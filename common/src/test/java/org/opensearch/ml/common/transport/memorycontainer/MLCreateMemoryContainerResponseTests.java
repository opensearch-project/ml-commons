/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MLCreateMemoryContainerResponseTests {

    private MLCreateMemoryContainerResponse responseSuccess;
    private MLCreateMemoryContainerResponse responseCreated;
    private MLCreateMemoryContainerResponse responseWithLongId;

    @Before
    public void setUp() {
        // Response with success status
        responseSuccess = new MLCreateMemoryContainerResponse("memory-container-123", "success");

        // Response with created status
        responseCreated = new MLCreateMemoryContainerResponse("memory-container-456", "created");

        // Response with long ID to test edge cases
        responseWithLongId = new MLCreateMemoryContainerResponse(
            "memory-container-with-very-long-id-that-contains-multiple-segments-and-special-characters-789",
            "success"
        );
    }

    @Test
    public void testConstructorWithParameters() {
        assertNotNull(responseSuccess);
        assertEquals("memory-container-123", responseSuccess.getMemoryContainerId());
        assertEquals("success", responseSuccess.getStatus());

        assertNotNull(responseCreated);
        assertEquals("memory-container-456", responseCreated.getMemoryContainerId());
        assertEquals("created", responseCreated.getStatus());
    }

    @Test
    public void testConstructorWithLongId() {
        assertNotNull(responseWithLongId);
        assertEquals(
            "memory-container-with-very-long-id-that-contains-multiple-segments-and-special-characters-789",
            responseWithLongId.getMemoryContainerId()
        );
        assertEquals("success", responseWithLongId.getStatus());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseSuccess.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse parsedResponse = new MLCreateMemoryContainerResponse(streamInput);

        assertNotNull(parsedResponse);
        assertEquals(responseSuccess.getMemoryContainerId(), parsedResponse.getMemoryContainerId());
        assertEquals(responseSuccess.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testStreamInputOutputWithDifferentStatus() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseCreated.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse parsedResponse = new MLCreateMemoryContainerResponse(streamInput);

        assertNotNull(parsedResponse);
        assertEquals(responseCreated.getMemoryContainerId(), parsedResponse.getMemoryContainerId());
        assertEquals(responseCreated.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testStreamInputOutputWithLongId() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseWithLongId.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse parsedResponse = new MLCreateMemoryContainerResponse(streamInput);

        assertNotNull(parsedResponse);
        assertEquals(responseWithLongId.getMemoryContainerId(), parsedResponse.getMemoryContainerId());
        assertEquals(responseWithLongId.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testToXContentWithSuccessStatus() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseSuccess.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains("\"memory_container_id\":\"memory-container-123\""));
        assertTrue(jsonStr.contains("\"status\":\"success\""));

        // Verify it's a proper JSON object
        assertTrue(jsonStr.startsWith("{"));
        assertTrue(jsonStr.endsWith("}"));
    }

    @Test
    public void testToXContentWithCreatedStatus() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseCreated.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        assertTrue(jsonStr.contains("\"memory_container_id\":\"memory-container-456\""));
        assertTrue(jsonStr.contains("\"status\":\"created\""));
    }

    @Test
    public void testToXContentWithLongId() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseWithLongId.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        assertTrue(
            jsonStr
                .contains(
                    "\"memory_container_id\":\"memory-container-with-very-long-id-that-contains-multiple-segments-and-special-characters-789\""
                )
        );
        assertTrue(jsonStr.contains("\"status\":\"success\""));
    }

    @Test
    public void testGetterMethods() {
        assertEquals("memory-container-123", responseSuccess.getMemoryContainerId());
        assertEquals("success", responseSuccess.getStatus());

        assertEquals("memory-container-456", responseCreated.getMemoryContainerId());
        assertEquals("created", responseCreated.getStatus());
    }

    @Test
    public void testInheritanceFromActionResponse() {
        assertTrue(responseSuccess instanceof ActionResponse);
        assertTrue(responseCreated instanceof ActionResponse);
        assertTrue(responseWithLongId instanceof ActionResponse);
    }

    @Test
    public void testToXContentObjectInterface() {
        assertTrue(responseSuccess instanceof org.opensearch.core.xcontent.ToXContentObject);
        assertTrue(responseCreated instanceof org.opensearch.core.xcontent.ToXContentObject);
    }

    @Test
    public void testCompleteRoundTripSerialization() throws IOException {
        // Test complete serialization round trip
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseSuccess.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse deserializedResponse = new MLCreateMemoryContainerResponse(streamInput);

        // Verify all data is preserved
        assertEquals(responseSuccess.getMemoryContainerId(), deserializedResponse.getMemoryContainerId());
        assertEquals(responseSuccess.getStatus(), deserializedResponse.getStatus());

        // Test that the deserialized response can be serialized again
        BytesStreamOutput secondOutput = new BytesStreamOutput();
        deserializedResponse.writeTo(secondOutput);

        StreamInput secondInput = secondOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse secondDeserialized = new MLCreateMemoryContainerResponse(secondInput);

        assertEquals(responseSuccess.getMemoryContainerId(), secondDeserialized.getMemoryContainerId());
        assertEquals(responseSuccess.getStatus(), secondDeserialized.getStatus());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Test JSON serialization and verify structure
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseSuccess.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        // Verify JSON structure contains expected fields
        assertTrue(jsonStr.contains("memory_container_id"));
        assertTrue(jsonStr.contains("status"));
        assertTrue(jsonStr.contains("memory-container-123"));
        assertTrue(jsonStr.contains("success"));
    }

    @Test
    public void testWithEmptyStrings() throws IOException {
        MLCreateMemoryContainerResponse responseWithEmptyStrings = new MLCreateMemoryContainerResponse("", "");

        assertEquals("", responseWithEmptyStrings.getMemoryContainerId());
        assertEquals("", responseWithEmptyStrings.getStatus());

        // Test serialization with empty strings
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseWithEmptyStrings.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse parsedResponse = new MLCreateMemoryContainerResponse(streamInput);

        assertEquals("", parsedResponse.getMemoryContainerId());
        assertEquals("", parsedResponse.getStatus());

        // Test JSON serialization with empty strings
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseWithEmptyStrings.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonStr.contains("\"memory_container_id\":\"\""));
        assertTrue(jsonStr.contains("\"status\":\"\""));
    }

    @Test
    public void testWithSpecialCharacters() throws IOException {
        MLCreateMemoryContainerResponse responseWithSpecialChars = new MLCreateMemoryContainerResponse(
            "memory-container-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?",
            "status-with-special-chars-!@#$%"
        );

        assertEquals("memory-container-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?", responseWithSpecialChars.getMemoryContainerId());
        assertEquals("status-with-special-chars-!@#$%", responseWithSpecialChars.getStatus());

        // Test serialization with special characters
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseWithSpecialChars.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerResponse parsedResponse = new MLCreateMemoryContainerResponse(streamInput);

        assertEquals(responseWithSpecialChars.getMemoryContainerId(), parsedResponse.getMemoryContainerId());
        assertEquals(responseWithSpecialChars.getStatus(), parsedResponse.getStatus());
    }

    @Test
    public void testFieldConstants() throws IOException {
        // Test that the response uses the correct field constants from MemoryContainerConstants
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseSuccess.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        // Verify that the correct field names are used
        assertTrue(jsonStr.contains("memory_container_id")); // MEMORY_CONTAINER_ID_FIELD
        assertTrue(jsonStr.contains("status")); // STATUS_FIELD
    }

    @Test
    public void testMultipleInstancesIndependence() {
        // Test that multiple instances don't interfere with each other
        MLCreateMemoryContainerResponse response1 = new MLCreateMemoryContainerResponse("id1", "status1");
        MLCreateMemoryContainerResponse response2 = new MLCreateMemoryContainerResponse("id2", "status2");

        assertEquals("id1", response1.getMemoryContainerId());
        assertEquals("status1", response1.getStatus());
        assertEquals("id2", response2.getMemoryContainerId());
        assertEquals("status2", response2.getStatus());

        // Verify they don't affect each other
        assertNotEquals(response1.getMemoryContainerId(), response2.getMemoryContainerId());
        assertNotEquals(response1.getStatus(), response2.getStatus());
    }

    @Test
    public void testLombokGetterAnnotation() {
        // Test that @Getter annotation works correctly
        assertNotNull(responseSuccess.getMemoryContainerId());
        assertNotNull(responseSuccess.getStatus());

        // Test that getters return the correct values
        assertEquals("memory-container-123", responseSuccess.getMemoryContainerId());
        assertEquals("success", responseSuccess.getStatus());
    }

    // Helper method for assertions
    private void assertNotEquals(Object obj1, Object obj2) {
        org.junit.Assert.assertNotEquals(obj1, obj2);
    }
}
