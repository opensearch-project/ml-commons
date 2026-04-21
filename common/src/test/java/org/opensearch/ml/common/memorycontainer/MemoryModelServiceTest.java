/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.agent.MLAgentModelSpec;

public class MemoryModelServiceTest {

    @Test
    public void testCreateModelFromSpec_bedrockEmbedding() {
        MLAgentModelSpec spec = MLAgentModelSpec.builder()
            .modelId("amazon.titan-embed-text-v2:0")
            .modelProvider("bedrock/embedding")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testCreateModelFromSpec_bedrockConverseLLM() {
        MLAgentModelSpec spec = MLAgentModelSpec.builder()
            .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
            .modelProvider("bedrock/converse")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testCreateModelFromSpec_nullSpec() {
        assertThrows(IllegalArgumentException.class, () -> MemoryModelService.createModelFromSpec(null));
    }

    @Test
    public void testCreateModelFromSpec_nullModelId() {
        assertThrows(IllegalArgumentException.class, () ->
            MemoryModelService.createModelFromSpec(
                MLAgentModelSpec.builder().modelId(null).modelProvider("bedrock/embedding").build()
            )
        );
    }

    @Test
    public void testCreateModelFromSpec_nullProvider() {
        assertThrows(IllegalArgumentException.class, () ->
            MemoryModelService.createModelFromSpec(
                MLAgentModelSpec.builder().modelId("test").modelProvider(null).build()
            )
        );
    }

    @Test
    public void testDetectEmbeddingType_knownModel() {
        assertEquals(FunctionName.TEXT_EMBEDDING, MemoryModelService.detectEmbeddingType("amazon.titan-embed-text-v2:0"));
    }

    @Test
    public void testDetectEmbeddingType_unknownModel() {
        assertNull(MemoryModelService.detectEmbeddingType("unknown-model"));
    }

    @Test
    public void testDetectEmbeddingDimension_knownModel() {
        assertEquals(Integer.valueOf(1024), MemoryModelService.detectEmbeddingDimension("amazon.titan-embed-text-v2:0"));
        assertEquals(Integer.valueOf(1536), MemoryModelService.detectEmbeddingDimension("amazon.titan-embed-text-v1"));
    }

    @Test
    public void testDetectEmbeddingDimension_unknownModel() {
        assertNull(MemoryModelService.detectEmbeddingDimension("unknown-model"));
    }
}
