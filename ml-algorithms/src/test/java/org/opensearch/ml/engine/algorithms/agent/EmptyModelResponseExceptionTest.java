/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensearch.core.rest.RestStatus;

public class EmptyModelResponseExceptionTest {

    @Test
    public void testNormalStopOrNoReasonIsRetryable() {
        // A normal stop with nothing in it is the intermittent case a second attempt usually fixes.
        for (String reason : new String[] { null, "stop", "STOP", "end_turn", "stop_sequence", "FINISH_REASON_UNSPECIFIED" }) {
            assertTrue(String.valueOf(reason), new EmptyModelResponseException("P", reason).isRetryable());
        }
    }

    @Test
    public void testMalformedReplyIsRetryable() {
        // Bedrock and Gemini report a badly formed reply this way; a second sample usually comes out fine.
        for (String reason : new String[] { "malformed_model_output", "malformed_tool_use", "MALFORMED_FUNCTION_CALL", "OTHER" }) {
            assertTrue(reason, new EmptyModelResponseException("P", reason).isRetryable());
        }
    }

    @Test
    public void testTokenLimitIsNotRetryable() {
        // OpenAI "length", Anthropic/Bedrock "max_tokens", Gemini "MAX_TOKENS": the same request hits the same limit.
        for (String reason : new String[] { "length", "max_tokens", "MAX_TOKENS" }) {
            assertFalse(reason, new EmptyModelResponseException("P", reason).isRetryable());
        }
    }

    @Test
    public void testBlockedOutputIsNotRetryable() {
        for (String reason : new String[] {
            "content_filter",
            "content_filtered",
            "guardrail_intervened",
            "refusal",
            "SAFETY",
            "RECITATION",
            "IMAGE_PROHIBITED_CONTENT",
            "IMAGE_RECITATION",
            "BLOCK_REASON_UNSPECIFIED" }) {
            assertFalse(reason, new EmptyModelResponseException("P", reason).isRetryable());
            assertTrue(reason, new EmptyModelResponseException("P", reason).getMessage().contains("content filter or guardrail"));
        }
    }

    @Test
    public void testOtherReasonsAreNotRetryable() {
        for (String reason : new String[] { "model_context_window_exceeded", "LANGUAGE", "something_new" }) {
            assertFalse(reason, new EmptyModelResponseException("P", reason).isRetryable());
        }
    }

    @Test
    public void testMessageExplainsAContextWindowOverflow() {
        String message = new EmptyModelResponseException("BedrockConverseModelProvider", "model_context_window_exceeded").getMessage();
        assertTrue(message.contains("shorten the conversation"));
        assertFalse(message.contains("increase max_tokens"));
    }

    @Test
    public void testMessageNamesTheFinishReasonAndExplainsATokenLimit() {
        String message = new EmptyModelResponseException("P", "length").getMessage();
        assertTrue(message.startsWith("The model returned an empty response (no text and no tool calls), finish reason: length."));
        assertTrue(message.contains("increase max_tokens"));
    }

    @Test
    public void testMessageExplainsABlockedOutput() {
        assertTrue(new EmptyModelResponseException("P", "SAFETY").getMessage().contains("content filter or guardrail"));
    }

    @Test
    public void testMessageWithoutAFinishReason() {
        assertEquals(
            "The model returned an empty response (no text and no tool calls).",
            new EmptyModelResponseException("P", null).getMessage()
        );
    }

    @Test
    public void testIsABadGatewayCarryingProviderAndReason() {
        EmptyModelResponseException e = new EmptyModelResponseException("OpenaiV1ChatCompletionsModelProvider", "stop");
        assertEquals(RestStatus.BAD_GATEWAY, e.status());
        assertEquals("OpenaiV1ChatCompletionsModelProvider", e.getProvider());
        assertEquals("stop", e.getFinishReason());
    }

    @Test
    public void testIsABadRequestWhenTheRequestMustChange() {
        // A token limit, a full context window or a block recur on the same request, so the client has to change it.
        for (String reason : new String[] { "length", "MAX_TOKENS", "model_context_window_exceeded", "SAFETY", "refusal" }) {
            assertEquals(reason, RestStatus.BAD_REQUEST, new EmptyModelResponseException("P", reason).status());
        }
        for (String reason : new String[] { null, "stop", "malformed_tool_use", "something_new" }) {
            assertEquals(String.valueOf(reason), RestStatus.BAD_GATEWAY, new EmptyModelResponseException("P", reason).status());
        }
    }
}
