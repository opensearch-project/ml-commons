/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.Locale;
import java.util.Set;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;

import lombok.Getter;

/**
 * The model returned a well-formed response with no text and no tool calls.
 *
 * Distinct from an unparseable response: the reply is valid, just empty. Whether retrying can help
 * depends on why the model stopped. A transient empty reply (a normal stop with nothing in it, seen
 * intermittently with some models) usually succeeds on a second attempt. An empty reply caused by
 * the output token limit or a content filter fails the same way again, so it is reported at once.
 */
@Getter
public class EmptyModelResponseException extends OpenSearchStatusException {

    /** A normal stop, or no reason given: the intermittent case a second attempt usually fixes. */
    private static final Set<String> RETRYABLE_REASONS = Set.of("", "stop", "end_turn", "stop_sequence", "finish_reason_unspecified");
    /** Output limit reached before any text: OpenAI "length", Anthropic and Bedrock "max_tokens", Gemini "MAX_TOKENS". */
    private static final Set<String> TOKEN_LIMIT_REASONS = Set.of("length", "max_tokens");
    /** Bedrock Converse: the input already fills the model's context window. */
    private static final String CONTEXT_WINDOW_REASON = "model_context_window_exceeded";
    /** Output blocked by the provider: OpenAI, Bedrock and Gemini safety and policy stop reasons. */
    private static final Set<String> BLOCKED_REASONS = Set
        .of(
            "content_filter",
            "content_filtered",
            "guardrail_intervened",
            "refusal",
            "safety",
            "recitation",
            "blocklist",
            "prohibited_content",
            "spii",
            "image_safety",
            "image_prohibited_content",
            "image_recitation"
        );

    private final String provider;
    private final String finishReason;

    public EmptyModelResponseException(String provider, String finishReason) {
        super(message(finishReason), RestStatus.BAD_GATEWAY);
        this.provider = provider;
        this.finishReason = finishReason;
    }

    /** True only for a normal stop or no reason; any other reason would recur on the same request. */
    public boolean isRetryable() {
        return RETRYABLE_REASONS.contains(normalized(finishReason));
    }

    private static String message(String finishReason) {
        String reason = normalized(finishReason);
        StringBuilder text = new StringBuilder("The model returned an empty response (no text and no tool calls)");
        if (finishReason != null) {
            text.append(", finish reason: ").append(finishReason);
        }
        if (TOKEN_LIMIT_REASONS.contains(reason)) {
            text.append(". The output token limit was reached before any text; increase max_tokens or reduce the reasoning budget");
        } else if (CONTEXT_WINDOW_REASON.equals(reason)) {
            text.append(". The input filled the model's context window; shorten the conversation or the tool output");
        } else if (BLOCKED_REASONS.contains(reason)) {
            text.append(". The provider's content filter or guardrail may have blocked the request or the output");
        }
        return text.append('.').toString();
    }

    private static String normalized(String finishReason) {
        return finishReason == null ? "" : finishReason.trim().toLowerCase(Locale.ROOT);
    }
}
