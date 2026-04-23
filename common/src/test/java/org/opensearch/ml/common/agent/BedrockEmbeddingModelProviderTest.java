/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.memorycontainer.EmbeddingModelInfo;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class BedrockEmbeddingModelProviderTest {

    private final BedrockEmbeddingModelProvider provider = new BedrockEmbeddingModelProvider();
    private final Map<String, String> testCredential = Map.of("access_key", "test_key", "secret_key", "test_secret");

    @Test
    public void testCreateConnector_knownModel() {
        Connector connector = provider.createConnector("amazon.titan-embed-text-v2:0", testCredential, null);

        assertNotNull(connector);
        assertEquals("aws_sigv4", connector.getProtocol());
        assertEquals("amazon.titan-embed-text-v2:0", connector.getParameters().get("model"));
        assertEquals("1024", connector.getParameters().get("dimensions"));
        assertEquals("bedrock", connector.getParameters().get("service_name"));
    }

    @Test
    public void testCreateConnector_withRegionOverride() {
        Map<String, String> params = new HashMap<>();
        params.put("region", "us-west-2");

        Connector connector = provider.createConnector("amazon.titan-embed-text-v2:0", testCredential, params);
        assertEquals("us-west-2", connector.getParameters().get("region"));
    }

    @Test
    public void testCreateModelInput_knownDenseModel() {
        Connector connector = provider.createConnector("amazon.titan-embed-text-v2:0", testCredential, null);
        MLRegisterModelInput input = provider.createModelInput("amazon.titan-embed-text-v2:0", connector, null);

        assertEquals(FunctionName.REMOTE, input.getFunctionName());
        assertNotNull(input.getModelName());
        assertNotNull(input.getConnector());
    }

    @Test
    public void testCreateModelInput_unknownModel_defaultsToRemote() {
        Connector connector = provider.createConnector("some.unknown-model", testCredential, null);
        MLRegisterModelInput input = provider.createModelInput("some.unknown-model", connector, null);

        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testGetModelInfo_knownModels() {
        EmbeddingModelInfo titanV2 = BedrockEmbeddingModelProvider.getModelInfo("amazon.titan-embed-text-v2:0");
        assertNotNull(titanV2);
        assertEquals(FunctionName.TEXT_EMBEDDING, titanV2.functionName);
        assertEquals(1024, titanV2.dimension);

        EmbeddingModelInfo titanV1 = BedrockEmbeddingModelProvider.getModelInfo("amazon.titan-embed-text-v1");
        assertNotNull(titanV1);
        assertEquals(1536, titanV1.dimension);

        EmbeddingModelInfo cohere = BedrockEmbeddingModelProvider.getModelInfo("cohere.embed-english-v3");
        assertNotNull(cohere);
        assertEquals(1024, cohere.dimension);
    }

    @Test
    public void testGetModelInfo_unknownModel() {
        assertNull(BedrockEmbeddingModelProvider.getModelInfo("unknown-model"));
    }

    @Test
    public void testGetLLMInterface_returnsNull() {
        assertNull(provider.getLLMInterface());
    }

    @Test
    public void testMapTextInput_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> provider.mapTextInput("test", null));
    }

    @Test
    public void testMapMessages_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> provider.mapMessages(null, null));
    }

    @Test
    public void testParseToUnifiedMessage_throwsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> provider.parseToUnifiedMessage("{}"));
    }
}
