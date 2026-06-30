/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FunctionCallingCapabilityTests {

    @Test
    public void testFunctionCalling_defaultSupportsStrictSchema() {
        // Test the default implementation
        FunctionCalling defaultImpl = new FunctionCalling() {
            @Override
            public void configure(java.util.Map<String, String> params) {}

            @Override
            public java.util.List<java.util.Map<String, String>> handle(
                org.opensearch.ml.common.output.model.ModelTensorOutput modelTensorOutput,
                java.util.Map<String, String> parameters
            ) {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<LLMMessage> supply(java.util.List<java.util.Map<String, Object>> toolResults) {
                return java.util.Collections.emptyList();
            }
        };

        assertFalse("Default implementation should return false for supportsStrictSchema", defaultImpl.supportsStrictSchema());
    }

    @Test
    public void testOpenaiV1ChatCompletionsFunctionCalling_supportsStrictSchema() {
        // Test OpenAI implementation
        OpenaiV1ChatCompletionsFunctionCalling openaiImpl = new OpenaiV1ChatCompletionsFunctionCalling();

        assertTrue("OpenAI implementation should return true for supportsStrictSchema", openaiImpl.supportsStrictSchema());
    }

    @Test
    public void testBedrockConverseFunctionCalling_supportsStrictSchema() {
        // Test Bedrock implementation (should use default = false)
        BedrockConverseFunctionCalling bedrockImpl = new BedrockConverseFunctionCalling();

        assertFalse(
            "Bedrock implementation should return false for supportsStrictSchema (uses framework validation)",
            bedrockImpl.supportsStrictSchema()
        );
    }

    // Test that demonstrates the capability-driven approach
    @Test
    public void testCapabilityDrivenValidation_concept() {
        FunctionCalling[] providers = {
            new OpenaiV1ChatCompletionsFunctionCalling(),  // supports strict schema
            new BedrockConverseFunctionCalling()           // needs framework validation
        };

        for (FunctionCalling provider : providers) {
            if (provider.supportsStrictSchema()) {
                // Provider handles schema enforcement natively
                assertTrue("OpenAI should support strict schema", provider instanceof OpenaiV1ChatCompletionsFunctionCalling);
            } else {
                // Framework needs to validate input
                assertTrue(
                    "Non-OpenAI providers should need framework validation",
                    !(provider instanceof OpenaiV1ChatCompletionsFunctionCalling)
                );
            }
        }
    }
}
