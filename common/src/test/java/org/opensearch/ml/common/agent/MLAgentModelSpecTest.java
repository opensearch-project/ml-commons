/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class MLAgentModelSpecTest {

    @Test
    public void testConstructor_WithAllParameters() {
        // Arrange
        String modelId = "test-model-id";
        String modelProvider = "bedrock";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");
        modelParameters.put("temperature", "0.7");

        // Act
        MLAgentModelSpec spec = new MLAgentModelSpec(modelId, modelProvider, credential, modelParameters);

        // Assert
        assertEquals(modelId, spec.getModelId());
        assertEquals(modelProvider, spec.getModelProvider());
        assertEquals(credential, spec.getCredential());
        assertEquals(modelParameters, spec.getModelParameters());
    }

    @Test
    public void testConstructor_WithMinimalParameters() {
        // Arrange
        String modelId = "test-model-id";
        String modelProvider = "openai";

        // Act
        MLAgentModelSpec spec = new MLAgentModelSpec(modelId, modelProvider, null, null);

        // Assert
        assertEquals(modelId, spec.getModelId());
        assertEquals(modelProvider, spec.getModelProvider());
        assertNull(spec.getCredential());
        assertNull(spec.getModelParameters());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullModelId_ThrowsException() {
        // Act
        new MLAgentModelSpec(null, "bedrock", null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullModelProvider_ThrowsException() {
        // Act
        new MLAgentModelSpec("test-model-id", null, null, null);
    }

    @Test
    public void testBuilder() {
        // Arrange
        String modelId = "test-model-id";
        String modelProvider = "bedrock";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");

        // Act
        MLAgentModelSpec spec = MLAgentModelSpec.builder()
            .modelId(modelId)
            .modelProvider(modelProvider)
            .credential(credential)
            .build();

        // Assert
        assertEquals(modelId, spec.getModelId());
        assertEquals(modelProvider, spec.getModelProvider());
        assertEquals(credential, spec.getCredential());
    }

    @Test
    public void testToBuilder() {
        // Arrange
        String modelId = "test-model-id";
        String modelProvider = "bedrock";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        
        MLAgentModelSpec original = MLAgentModelSpec.builder()
            .modelId(modelId)
            .modelProvider(modelProvider)
            .credential(credential)
            .build();

        // Act
        MLAgentModelSpec copy = original.toBuilder().build();

        // Assert
        assertEquals(original, copy);
        assertEquals(original.getModelId(), copy.getModelId());
        assertEquals(original.getModelProvider(), copy.getModelProvider());
        assertEquals(original.getCredential(), copy.getCredential());
    }

    @Test
    public void testRemoveCredential() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        
        MLAgentModelSpec spec = new MLAgentModelSpec("model-id", "provider", credential, null);
        assertNotNull(spec.getCredential());

        // Act
        spec.removeCredential();

        // Assert
        assertNull(spec.getCredential());
    }

    @Test
    public void testStreamSerialization_WithAllFields() throws IOException {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");
        modelParameters.put("temperature", "0.7");
        
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            credential,
            modelParameters
        );

        // Act
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        MLAgentModelSpec deserialized = new MLAgentModelSpec(input);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertEquals(original.getCredential(), deserialized.getCredential());
        assertEquals(original.getModelParameters(), deserialized.getModelParameters());
    }

    @Test
    public void testStreamSerialization_WithNullCredential() throws IOException {
        // Arrange
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-east-1");
        
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "openai",
            null,
            modelParameters
        );

        // Act
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        MLAgentModelSpec deserialized = new MLAgentModelSpec(input);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertNull(deserialized.getCredential());
        assertEquals(original.getModelParameters(), deserialized.getModelParameters());
    }

    @Test
    public void testStreamSerialization_WithEmptyMaps() throws IOException {
        // Arrange
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            new HashMap<>(),
            new HashMap<>()
        );

        // Act
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        MLAgentModelSpec deserialized = new MLAgentModelSpec(input);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertNull(deserialized.getCredential());
        assertNull(deserialized.getModelParameters());
    }

    @Test
    public void testStreamSerialization_MinimalFields() throws IOException {
        // Arrange
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            null,
            null
        );

        // Act
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        MLAgentModelSpec deserialized = new MLAgentModelSpec(input);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertNull(deserialized.getCredential());
        assertNull(deserialized.getModelParameters());
    }

    @Test
    public void testFromStream() throws IOException {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            credential,
            null
        );

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        StreamInput input = output.bytes().streamInput();

        // Act
        MLAgentModelSpec deserialized = MLAgentModelSpec.fromStream(input);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertEquals(original.getCredential(), deserialized.getCredential());
    }

    @Test
    public void testToXContent_WithAllFields() throws IOException {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");
        
        MLAgentModelSpec spec = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            credential,
            modelParameters
        );

        // Act
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonString = builder.toString();

        // Assert
        assertTrue(jsonString.contains("\"model_id\":\"test-model-id\""));
        assertTrue(jsonString.contains("\"model_provider\":\"bedrock\""));
        assertTrue(jsonString.contains("\"credential\""));
        assertTrue(jsonString.contains("\"model_parameters\""));
    }

    @Test
    public void testToXContent_WithMinimalFields() throws IOException {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec(
            "test-model-id",
            "openai",
            null,
            null
        );

        // Act
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonString = builder.toString();

        // Assert
        assertTrue(jsonString.contains("\"model_id\":\"test-model-id\""));
        assertTrue(jsonString.contains("\"model_provider\":\"openai\""));
        assertFalse(jsonString.contains("\"credential\""));
        assertFalse(jsonString.contains("\"model_parameters\""));
    }

    @Test
    public void testToXContent_WithEmptyMaps() throws IOException {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            new HashMap<>(),
            new HashMap<>()
        );

        // Act
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonString = builder.toString();

        // Assert
        assertTrue(jsonString.contains("\"model_id\":\"test-model-id\""));
        assertTrue(jsonString.contains("\"model_provider\":\"bedrock\""));
        assertFalse(jsonString.contains("\"credential\""));
        assertFalse(jsonString.contains("\"model_parameters\""));
    }

    @Test
    public void testParse_WithAllFields() throws IOException {
        // Arrange
        String jsonString = "{\"model_id\":\"test-model-id\",\"model_provider\":\"bedrock\"," +
            "\"credential\":{\"access_key\":\"test_key\",\"secret_key\":\"test_secret\"}," +
            "\"model_parameters\":{\"region\":\"us-west-2\",\"temperature\":\"0.7\"}}";

        XContentParser parser = createParser(jsonString);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        // Act
        MLAgentModelSpec spec = MLAgentModelSpec.parse(parser);

        // Assert
        assertEquals("test-model-id", spec.getModelId());
        assertEquals("bedrock", spec.getModelProvider());
        assertNotNull(spec.getCredential());
        assertEquals("test_key", spec.getCredential().get("access_key"));
        assertEquals("test_secret", spec.getCredential().get("secret_key"));
        assertNotNull(spec.getModelParameters());
        assertEquals("us-west-2", spec.getModelParameters().get("region"));
        assertEquals("0.7", spec.getModelParameters().get("temperature"));
    }

    @Test
    public void testParse_WithMinimalFields() throws IOException {
        // Arrange
        String jsonString = "{\"model_id\":\"test-model-id\",\"model_provider\":\"openai\"}";

        XContentParser parser = createParser(jsonString);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        // Act
        MLAgentModelSpec spec = MLAgentModelSpec.parse(parser);

        // Assert
        assertEquals("test-model-id", spec.getModelId());
        assertEquals("openai", spec.getModelProvider());
        assertNull(spec.getCredential());
        assertNull(spec.getModelParameters());
    }

    @Test
    public void testParse_WithUnknownFields() throws IOException {
        // Arrange
        String jsonString = "{\"model_id\":\"test-model-id\",\"model_provider\":\"bedrock\"," +
            "\"unknown_field\":\"value\",\"another_unknown\":{\"key\":\"value\"}}";

        XContentParser parser = createParser(jsonString);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        // Act
        MLAgentModelSpec spec = MLAgentModelSpec.parse(parser);

        // Assert
        assertEquals("test-model-id", spec.getModelId());
        assertEquals("bedrock", spec.getModelProvider());
    }

    @Test
    public void testEquals_SameObject() {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec("model-id", "provider", null, null);

        // Act & Assert
        assertEquals(spec, spec);
    }

    @Test
    public void testEquals_EqualObjects() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("key", "value");
        
        MLAgentModelSpec spec1 = new MLAgentModelSpec("model-id", "provider", credential, null);
        MLAgentModelSpec spec2 = new MLAgentModelSpec("model-id", "provider", credential, null);

        // Act & Assert
        assertEquals(spec1, spec2);
        assertEquals(spec1.hashCode(), spec2.hashCode());
    }

    @Test
    public void testEquals_DifferentModelId() {
        // Arrange
        MLAgentModelSpec spec1 = new MLAgentModelSpec("model-id-1", "provider", null, null);
        MLAgentModelSpec spec2 = new MLAgentModelSpec("model-id-2", "provider", null, null);

        // Act & Assert
        assertNotEquals(spec1, spec2);
    }

    @Test
    public void testEquals_DifferentModelProvider() {
        // Arrange
        MLAgentModelSpec spec1 = new MLAgentModelSpec("model-id", "provider1", null, null);
        MLAgentModelSpec spec2 = new MLAgentModelSpec("model-id", "provider2", null, null);

        // Act & Assert
        assertNotEquals(spec1, spec2);
    }

    @Test
    public void testEquals_DifferentCredential() {
        // Arrange
        Map<String, String> credential1 = new HashMap<>();
        credential1.put("key", "value1");
        
        Map<String, String> credential2 = new HashMap<>();
        credential2.put("key", "value2");
        
        MLAgentModelSpec spec1 = new MLAgentModelSpec("model-id", "provider", credential1, null);
        MLAgentModelSpec spec2 = new MLAgentModelSpec("model-id", "provider", credential2, null);

        // Act & Assert
        assertNotEquals(spec1, spec2);
    }

    @Test
    public void testEquals_Null() {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec("model-id", "provider", null, null);

        // Act & Assert
        assertNotEquals(spec, null);
    }

    @Test
    public void testEquals_DifferentClass() {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec("model-id", "provider", null, null);

        // Act & Assert
        assertNotEquals(spec, "not a MLAgentModelSpec");
    }

    @Test
    public void testSetters() {
        // Arrange
        MLAgentModelSpec spec = new MLAgentModelSpec("model-id", "provider", null, null);
        
        Map<String, String> newCredential = new HashMap<>();
        newCredential.put("new_key", "new_value");
        
        Map<String, String> newParameters = new HashMap<>();
        newParameters.put("param", "value");

        // Act
        spec.setCredential(newCredential);
        spec.setModelParameters(newParameters);

        // Assert
        assertEquals(newCredential, spec.getCredential());
        assertEquals(newParameters, spec.getModelParameters());
    }

    @Test
    public void testRoundTripSerialization() throws IOException {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");
        modelParameters.put("temperature", "0.7");
        
        MLAgentModelSpec original = new MLAgentModelSpec(
            "test-model-id",
            "bedrock",
            credential,
            modelParameters
        );

        // Act - Serialize to XContent
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        
        // Parse back from XContent
        XContentParser parser = createParser(builder.toString());
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLAgentModelSpec deserialized = MLAgentModelSpec.parse(parser);

        // Assert
        assertEquals(original.getModelId(), deserialized.getModelId());
        assertEquals(original.getModelProvider(), deserialized.getModelProvider());
        assertEquals(original.getCredential(), deserialized.getCredential());
        assertEquals(original.getModelParameters(), deserialized.getModelParameters());
    }

    private XContentParser createParser(String jsonString) throws IOException {
        XContentParser parser = XContentType.JSON.xContent()
            .createParser(NamedXContentRegistry.EMPTY, null, jsonString);
        return parser;
    }
}
