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
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;

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
        MLAgentModelSpec spec = MLAgentModelSpec.builder().modelId(modelId).modelProvider(modelProvider).credential(credential).build();

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

        MLAgentModelSpec original = MLAgentModelSpec.builder().modelId(modelId).modelProvider(modelProvider).credential(credential).build();

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

        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "bedrock", credential, modelParameters);

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

        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "openai", null, modelParameters);

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
        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "bedrock", new HashMap<>(), new HashMap<>());

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
        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "bedrock", null, null);

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

        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "bedrock", credential, null);

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

        MLAgentModelSpec spec = new MLAgentModelSpec("test-model-id", "bedrock", credential, modelParameters);

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
        MLAgentModelSpec spec = new MLAgentModelSpec("test-model-id", "openai", null, null);

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
        MLAgentModelSpec spec = new MLAgentModelSpec("test-model-id", "bedrock", new HashMap<>(), new HashMap<>());

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
        String jsonString = "{\"model_id\":\"test-model-id\",\"model_provider\":\"bedrock\","
            + "\"credential\":{\"access_key\":\"test_key\",\"secret_key\":\"test_secret\"},"
            + "\"model_parameters\":{\"region\":\"us-west-2\",\"temperature\":\"0.7\"}}";

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
        String jsonString = "{\"model_id\":\"test-model-id\",\"model_provider\":\"bedrock\","
            + "\"unknown_field\":\"value\",\"another_unknown\":{\"key\":\"value\"}}";

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

        MLAgentModelSpec original = new MLAgentModelSpec("test-model-id", "bedrock", credential, modelParameters);

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
        XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, jsonString);
        return parser;
    }

    private MLAgentModelSpec parseSpec(String json) throws IOException {
        XContentParser parser = createParser(json);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        return MLAgentModelSpec.parse(parser);
    }

    private static String withClientConfig(String clientConfigJson) {
        return "{\"model_id\":\"m\",\"model_provider\":\"openai/v1/chat/completions\",\"client_config\":" + clientConfigJson + "}";
    }

    @Test
    public void testParse_ClientConfigKeepsUnsetFieldsNull() throws IOException {
        // Only read_timeout was set; everything else must stay null so the provider's defaults survive.
        ConnectorClientConfig config = parseSpec(withClientConfig("{\"read_timeout\":120}")).getClientConfig();
        assertEquals(Integer.valueOf(120), config.getReadTimeout());
        assertNull(config.getConnectionTimeout());
        assertNull(config.getMaxRetryTimes());
        assertNull(config.getMaxConnections());
        assertNull(config.getRetryBackoffPolicy());
    }

    @Test
    public void testParse_ClientConfigAllSupportedFields() throws IOException {
        ConnectorClientConfig config = parseSpec(
            withClientConfig(
                "{\"read_timeout\":120,\"connection_timeout\":10,\"max_connection\":20,\"max_retry_times\":5,"
                    + "\"retry_backoff_millis\":500,\"retry_timeout_seconds\":90,\"retry_backoff_policy\":\"exponential_full_jitter\"}"
            )
        ).getClientConfig();
        assertEquals(Integer.valueOf(120), config.getReadTimeout());
        assertEquals(Integer.valueOf(10), config.getConnectionTimeout());
        assertEquals(Integer.valueOf(20), config.getMaxConnections());
        assertEquals(Integer.valueOf(5), config.getMaxRetryTimes());
        assertEquals(Integer.valueOf(500), config.getRetryBackoffMillis());
        assertEquals(Integer.valueOf(90), config.getRetryTimeoutSeconds());
        assertEquals(RetryBackoffPolicy.EXPONENTIAL_FULL_JITTER, config.getRetryBackoffPolicy());
    }

    @Test
    public void testParse_ClientConfigAbsentIsNull() throws IOException {
        assertNull(parseSpec("{\"model_id\":\"m\",\"model_provider\":\"p\"}").getClientConfig());
    }

    @Test
    public void testParse_ClientConfigNullIsUnset() throws IOException {
        MLAgentModelSpec spec = parseSpec("{\"model_id\":\"m\",\"model_provider\":\"p\",\"client_config\":null}");
        assertNull(spec.getClientConfig());
        assertEquals("m", spec.getModelId());
    }

    @Test
    public void testParse_ClientConfigRejectsTlsSettings() {
        for (String field : new String[] { "skip_ssl_verification", "mutual_tls_enabled", "keystore_type" }) {
            String value = field.equals("keystore_type") ? "\"PEM\"" : "true";
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> parseSpec(withClientConfig("{\"" + field + "\":" + value + "}"))
            );
            assertTrue(e.getMessage().contains("client_config." + field + " is not supported for an agent model"));
            assertTrue(e.getMessage().contains("read_timeout"));
        }
    }

    @Test
    public void testParse_ClientConfigRejectsNonPositiveValues() {
        for (String field : new String[] {
            "read_timeout",
            "connection_timeout",
            "max_connection",
            "retry_backoff_millis",
            "retry_timeout_seconds" }) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> parseSpec(withClientConfig("{\"" + field + "\":0}"))
            );
            assertEquals("client_config." + field + " must be greater than 0", e.getMessage());
        }
    }

    @Test
    public void testParse_ClientConfigMaxRetryTimes() throws IOException {
        // -1 means "retry until retry_timeout_seconds", as on standalone connectors.
        assertEquals(Integer.valueOf(-1), parseSpec(withClientConfig("{\"max_retry_times\":-1}")).getClientConfig().getMaxRetryTimes());
        assertThrows(IllegalArgumentException.class, () -> parseSpec(withClientConfig("{\"max_retry_times\":-2}")));
    }

    @Test
    public void testParse_ClientConfigRejectsNullFieldsByName() {
        for (String field : MLAgentModelSpec.SUPPORTED_CLIENT_CONFIG_FIELDS) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> parseSpec(withClientConfig("{\"" + field + "\":null}"))
            );
            assertEquals("client_config." + field + " must not be null", e.getMessage());
        }
    }

    @Test
    public void testParse_ClientConfigRejectsNonIntegerValues() {
        // intValue() would coerce these: 1.9 to 1, 0.5 to 0 (then "must be greater than 0"), "30" to 30.
        for (String value : new String[] { "1.9", "0.5", "\"30\"", "true", "3000000000" }) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> parseSpec(withClientConfig("{\"read_timeout\":" + value + "}"))
            );
            assertEquals(value, "client_config.read_timeout must be an integer", e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> parseSpec(withClientConfig("{\"max_retry_times\":1.5}")));
    }

    @Test
    public void testToXContent_ClientConfigWritesOnlySetFields() throws IOException {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("m")
            .modelProvider("p")
            .clientConfig(ConnectorClientConfig.builder().readTimeout(120).build())
            .build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"client_config\":{\"read_timeout\":120}"));
    }

    @Test
    public void testXContentRoundTrip_ClientConfig() throws IOException {
        MLAgentModelSpec original = MLAgentModelSpec
            .builder()
            .modelId("m")
            .modelProvider("p")
            .clientConfig(ConnectorClientConfig.builder().readTimeout(120).maxRetryTimes(5).build())
            .build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertEquals(original, parseSpec(builder.toString()));
    }

    @Test
    public void testStreamSerialization_ClientConfig() throws IOException {
        MLAgentModelSpec original = MLAgentModelSpec
            .builder()
            .modelId("m")
            .modelProvider("p")
            .clientConfig(ConnectorClientConfig.builder().readTimeout(120).build())
            .build();
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        MLAgentModelSpec deserialized = new MLAgentModelSpec(output.bytes().streamInput());
        assertEquals(Integer.valueOf(120), deserialized.getClientConfig().getReadTimeout());
        assertNull(deserialized.getClientConfig().getMaxRetryTimes());
    }

    @Test
    public void testStreamSerialization_ClientConfigNotSentToOlderNodes() throws IOException {
        // A 3.9 node doesn't know the field: it is neither written to nor read from its streams.
        MLAgentModelSpec original = MLAgentModelSpec
            .builder()
            .modelId("m")
            .modelProvider("p")
            .modelParameters(Map.of("k", "v"))
            .clientConfig(ConnectorClientConfig.builder().readTimeout(120).build())
            .build();
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(CommonValue.VERSION_3_9_0);
        original.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(CommonValue.VERSION_3_9_0);
        MLAgentModelSpec deserialized = new MLAgentModelSpec(input);
        assertNull(deserialized.getClientConfig());
        assertEquals(Map.of("k", "v"), deserialized.getModelParameters());
        assertEquals(0, input.available());
    }

    @Test
    public void testStreamSerialization_WithoutClientConfig() throws IOException {
        MLAgentModelSpec original = new MLAgentModelSpec("m", "p", null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        assertNull(new MLAgentModelSpec(output.bytes().streamInput()).getClientConfig());
    }
}
