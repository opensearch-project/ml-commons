/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Gen AI Semantic Attributes following OpenTelemetry conventions.
 * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/README.md
 *
 * These attributes ensure consistent tracing across all agent types
 * (ChatAgent, PlanExecuteReflectAgent, FlowAgent).
 */
public final class AgentSemanticAttributes {

    private AgentSemanticAttributes() {}

    // ============ Gen AI Standard Attributes ============

    /** Links all runs in a conversation (memory_id/session_id) */
    public static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");

    /** Unique identifier for this run (parent_interaction_id) */
    public static final AttributeKey<String> REQUEST_ID = AttributeKey.stringKey("gen_ai.request.id");

    /** AI system identifier (e.g., 'aws_bedrock', 'openai') */
    public static final AttributeKey<String> SYSTEM = AttributeKey.stringKey("gen_ai.system");

    /** Model ID (e.g., 'anthropic.claude-3-sonnet') */
    public static final AttributeKey<String> MODEL = AttributeKey.stringKey("gen_ai.request.model");

    /** Max output tokens requested */
    public static final AttributeKey<Long> MAX_TOKENS = AttributeKey.longKey("gen_ai.request.max_tokens");

    /** Sampling temperature */
    public static final AttributeKey<Double> TEMPERATURE = AttributeKey.doubleKey("gen_ai.request.temperature");

    /** Prompt tokens consumed */
    public static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");

    /** Completion tokens generated */
    public static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    /** Total tokens used */
    public static final AttributeKey<Long> TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

    /** Finish reason (e.g., 'end_turn', 'tool_use', 'max_tokens') */
    public static final AttributeKey<String> FINISH_REASON = AttributeKey.stringKey("gen_ai.response.finish_reasons");

    // ============ Tool/Function Calling ============

    /** Name of the tool being executed */
    public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");

    /** Unique ID for this tool call */
    public static final AttributeKey<String> TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call_id");

    /** Tool description */
    public static final AttributeKey<String> TOOL_DESCRIPTION = AttributeKey.stringKey("gen_ai.tool.description");

    /** Tool input parameters (truncated) */
    public static final AttributeKey<String> TOOL_INPUT = AttributeKey.stringKey("gen_ai.tool.input");

    /** Tool output result (truncated) */
    public static final AttributeKey<String> TOOL_OUTPUT = AttributeKey.stringKey("gen_ai.tool.output");

    /** Tool execution duration in milliseconds */
    public static final AttributeKey<Long> TOOL_DURATION_MS = AttributeKey.longKey("gen_ai.tool.duration_ms");

    // ============ Agent-Specific Attributes ============

    /** Agent type (e.g., 'ChatAgent', 'PlanExecuteReflectAgent', 'FlowAgent') */
    public static final AttributeKey<String> AGENT_TYPE = AttributeKey.stringKey("gen_ai.agent.type");

    /** Current iteration in agent loop */
    public static final AttributeKey<Long> AGENT_ITERATION = AttributeKey.longKey("gen_ai.agent.iteration");

    /** Max allowed iterations */
    public static final AttributeKey<Long> AGENT_MAX_ITERATIONS = AttributeKey.longKey("gen_ai.agent.max_iterations");

    /** Current graph node name */
    public static final AttributeKey<String> AGENT_NODE = AttributeKey.stringKey("gen_ai.agent.node");

    // ============ PER Agent Specific ============

    /** Step number in PER agent */
    public static final AttributeKey<Long> STEP_NUMBER = AttributeKey.longKey("gen_ai.agent.step_number");

    /** Step description */
    public static final AttributeKey<String> STEP_DESCRIPTION = AttributeKey.stringKey("gen_ai.agent.step_description");

    /** Executor agent ID */
    public static final AttributeKey<String> EXECUTOR_AGENT_ID = AttributeKey.stringKey("gen_ai.agent.executor_id");

    // ============ Result Attributes ============

    /** Whether the operation succeeded */
    public static final AttributeKey<Boolean> RESULT_SUCCESS = AttributeKey.booleanKey("result.success");

    /** Result output (truncated) */
    public static final AttributeKey<String> RESULT_OUTPUT = AttributeKey.stringKey("result.output");

    /** Error message if failed */
    public static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("error.message");

    // ============ Span Names ============

    public static final class SpanNames {
        private SpanNames() {}

        /** Root span for agent execution */
        public static final String AGENT_RUN = "agent.run";

        /** LLM inference call */
        public static final String LLM_INFERENCE = "llm.inference";

        /** Tool execution */
        public static final String TOOL_EXECUTE = "tool.execute";

        /** Step execution (PER agent) */
        public static final String STEP_EXECUTE = "step.execute";

        /** Planning phase (PER agent) */
        public static final String PLANNING = "planning";

        /** Reflection phase (PER agent) */
        public static final String REFLECTION = "reflection";
    }

    // ============ Event Names ============

    public static final class EventNames {
        private EventNames() {}

        public static final String LLM_REQUEST = "llm.request";
        public static final String LLM_RESPONSE = "llm.response";
        public static final String TOOL_INPUT = "tool.input";
        public static final String TOOL_OUTPUT = "tool.output";
        public static final String TOOL_ERROR = "tool.error";
        public static final String ITERATION_START = "iteration.start";
        public static final String ITERATION_END = "iteration.end";
    }

    // ============ Constants ============

    /** Maximum size for span attributes to avoid payload too large errors */
    public static final int MAX_ATTRIBUTE_SIZE = 8192;

    /**
     * Truncate a string for safe inclusion in spans.
     */
    public static String truncate(String value, int maxSize) {
        if (value == null) return "";
        if (value.length() <= maxSize) return value;
        return value.substring(0, maxSize) + "...[TRUNCATED]";
    }

    /**
     * Truncate using default max size.
     */
    public static String truncate(String value) {
        return truncate(value, MAX_ATTRIBUTE_SIZE);
    }
}
