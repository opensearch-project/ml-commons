/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.memorycontainer.EmbeddingModelInfo;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class OpenaiEmbeddingModelProviderTest {

    private final OpenaiEmbeddingModelProvider provider = new OpenaiEmbeddingModelProvider();
    private final Map<String, String> testCredential = Map.of("openAI_key", "test-key");

    @Test
    public void testCreateConnector() {
        Connector connector = provider.createConnector("text-embedding-3-small", testCredential, null);
        assertNotNull(connector);
        assertEquals("http", connector.getProtocol());
        assertEquals("text-embedding-3-small", connector.getParameters().get("model"));
    }

    @Test
    public void testCreateModelInput() {
        Connector connector = provider.createConnector("text-embedding-3-small", testCredential, null);
        MLRegisterModelInput input = provider.createModelInput("text-embedding-3-small", connector, null);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testGetModelInfo_knownModels() {
        EmbeddingModelInfo small = OpenaiEmbeddingModelProvider.getModelInfo("text-embedding-3-small");
        assertNotNull(small);
        assertEquals(1536, small.dimension());

        EmbeddingModelInfo large = OpenaiEmbeddingModelProvider.getModelInfo("text-embedding-3-large");
        assertNotNull(large);
        assertEquals(3072, large.dimension());
    }

    @Test
    public void testGetModelInfo_unknown() {
        assertNull(OpenaiEmbeddingModelProvider.getModelInfo("unknown"));
    }

    @Test
    public void testGetLLMInterface_returnsNull() {
        assertNull(provider.getLLMInterface());
    }

    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> provider.mapTextInput("t", null));
        assertThrows(UnsupportedOperationException.class, () -> provider.mapMessages(null, null));
        assertThrows(UnsupportedOperationException.class, () -> provider.mapContentBlocks(null, null));
        assertThrows(UnsupportedOperationException.class, () -> provider.extractMessageFromResponse(null));
        assertThrows(UnsupportedOperationException.class, () -> provider.parseToUnifiedMessage("{}"));
    }
}
