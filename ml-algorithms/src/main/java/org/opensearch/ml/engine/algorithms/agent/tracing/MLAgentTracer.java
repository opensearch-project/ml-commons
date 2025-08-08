/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.output.MLTaskOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

/**
 * MLAgentTracer is a concrete implementation of AbstractMLTracer for agent tracing in ML Commons.
 * It manages the lifecycle of agent-related spans, including creation, context propagation, and completion.
 * <p>
 * This class is implemented as a singleton to ensure that only one tracer is active
 * for agent tracing at any time. This design provides consistent management of tracing state and configuration,
 * and avoids issues with multiple tracers being active at once.
 * The singleton can be dynamically enabled or disabled based on cluster settings.
 * <p>
 * This class is thread-safe: multiple threads can use the singleton instance to start and end spans concurrently.
 * Each call to {@link #startSpan(String, Map, Span)} creates a new, independent span.
 */
@Log4j2
public class MLAgentTracer extends MLTracer {
    public static final String AGENT_TASK_SPAN = "agent.task";
    public static final String AGENT_CONV_TASK_SPAN = "agent.conv_task";
    public static final String AGENT_LLM_CALL_SPAN = "agent.llm_call";
    public static final String AGENT_TOOL_CALL_SPAN = "agent.tool_call";
    public static final String AGENT_PLAN_SPAN = "agent.plan";
    public static final String AGENT_EXECUTE_STEP_SPAN = "agent.execute_step";
    public static final String AGENT_REFLECT_STEP_SPAN = "agent.reflect_step";
    public static final String AGENT_TASK_PER_SPAN = "agent.task_per";
    public static final String AGENT_TASK_CONV_SPAN = "agent.task_conv";
    public static final String AGENT_TASK_CONV_FLOW_SPAN = "agent.task_convflow";
    public static final String AGENT_TASK_FLOW_SPAN = "agent.task_flow";
    public static final String FORMAT_REFLECT_STEP_SPAN = AGENT_REFLECT_STEP_SPAN + "_%d";
    public static final String FORMAT_EXECUTE_STEP_SPAN = AGENT_EXECUTE_STEP_SPAN + "_%d";

    public static final String SERVICE_TYPE_TRACER = "tracer";
    public static final String ATTR_RESULT = "gen_ai.agent.result";
    public static final String ATTR_TASK = "gen_ai.agent.task";
    public static final String ATTR_PHASE = "gen_ai.agent.phase";
    public static final String ATTR_AGENT_ID = "gen_ai.agent.id";
    public static final String ATTR_MODEL_ID = "gen_ai.model.id";
    public static final String ATTR_STEP_NUMBER = "gen_ai.agent.step.number";
    public static final String ATTR_NAME = "gen_ai.agent.name";
    public static final String ATTR_LATENCY = "gen_ai.agent.latency";
    public static final String ATTR_LLM_START = "llm.start_time";
    public static final String ATTR_SERVICE_TYPE = "service.type";
    public static final String ATTR_OPERATION_NAME = "gen_ai.operation.name";
    public static final String ATTR_SYSTEM = "gen_ai.system";
    public static final String ATTR_SYSTEM_MESSAGE = "gen_ai.system.message";
    public static final String ATTR_TOOL_DESCRIPTION = "gen_ai.tool.description";
    public static final String ATTR_TOOL_NAME = "gen_ai.tool.name";
    public static final String ATTR_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String ATTR_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String ATTR_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    public static final String PARAM_LLM_INTERFACE = "_llm_interface";
    public static final String PARAM_PROMPT = "prompt";
    public static final String PARAM_SYSTEM_PROMPT = "system_prompt";
    public static final String PARAM_TOOLS_PROMPT = "tools_prompt";
    public static final String USAGE_FIELD = "usage";
    public static final String METRICS_FIELD = "metrics";
    public static final String RESPONSE_FIELD = "response";
    public static final String OUTPUT_FIELD = "output";
    public static final String ADDITIONAL_INFO_FIELD = "additional_info";
    public static final String ADDITIONAL_INFO_FIELD_ALT = "additionalInfo";
    public static final String PROVIDER_BEDROCK = "bedrock";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_UNKNOWN = "unknown";
    public static final String TRACE_PARENT_FIELD = "traceparent";
    public static final String QUESTION_FIELD = "question";

    public static final String TOKEN_FIELD_INPUT_TOKENS = "inputTokens";
    public static final String TOKEN_FIELD_OUTPUT_TOKENS = "outputTokens";
    public static final String TOKEN_FIELD_TOTAL_TOKENS = "totalTokens";
    public static final String METRIC_FIELD_LATENCY_MS = "latencyMs";
    public static final String TOKEN_FIELD_INPUT_TOKENS_ALT = "input_tokens";
    public static final String TOKEN_FIELD_OUTPUT_TOKENS_ALT = "output_tokens";
    public static final String TOKEN_FIELD_PROMPT_TOKENS = "prompt_tokens";
    public static final String TOKEN_FIELD_COMPLETION_TOKENS = "completion_tokens";
    public static final String TOKEN_FIELD_TOTAL_TOKENS_ALT = "total_tokens";

    public enum OperationType {
        CHAT("chat"),
        CREATE_AGENT("create_agent"),
        EMBEDDINGS("embeddings"),
        EXECUTE_TOOL("execute_tool"),
        GENERATE_CONTENT("generate_content"),
        INVOKE_AGENT("invoke_agent"),
        TEXT_COMPLETION("text_completion");

        private final String value;

        OperationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum PhaseType {
        PLANNER("planner"),
        EXECUTOR("executor");

        private final String value;

        PhaseType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static MLAgentTracer instance;

    /**
     * Private constructor for MLAgentTracer.
     * @param tracer The tracer implementation to use (may be a real tracer or NoopTracer).
     * @param mlFeatureEnabledSetting The ML feature settings.
     */
    private MLAgentTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    /**
     * Initializes the singleton MLAgentTracer instance with the given tracer and settings.
     * This is a convenience method that calls the full initialize method with a null ClusterService.
     *
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     */
    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        initialize(tracer, mlFeatureEnabledSetting, null);
    }

    /**
     * Initializes the singleton MLAgentTracer instance with the given tracer and settings.
     * If agent tracing is disabled, a NoopTracer is used.
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     * @param clusterService The cluster service for dynamic settings updates. May be null.
     */
    public static synchronized void initialize(
        Tracer tracer,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ClusterService clusterService
    ) {
        Tracer tracerToUse = (mlFeatureEnabledSetting != null
            && mlFeatureEnabledSetting.isTracingEnabled()
            && mlFeatureEnabledSetting.isAgentTracingEnabled()
            && tracer != null) ? tracer : NoopTracer.INSTANCE;

        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLAgentTracer initialized with {}", tracerToUse.getClass().getSimpleName());

        if (clusterService != null) {
            clusterService.getClusterSettings().addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_AGENT_TRACING_ENABLED, enabled -> {
                Tracer newTracerToUse = (mlFeatureEnabledSetting != null
                    && mlFeatureEnabledSetting.isTracingEnabled()
                    && enabled
                    && tracer != null) ? tracer : NoopTracer.INSTANCE;
                instance = new MLAgentTracer(newTracerToUse, mlFeatureEnabledSetting);
                log.info("MLAgentTracer re-initialized with {} due to setting change", newTracerToUse.getClass().getSimpleName());
            });
        }
    }

    /**
     * Returns the singleton MLAgentTracer instance.
     * @return The MLAgentTracer instance.
     * @throws IllegalStateException if the tracer is not initialized.
     */
    public static synchronized MLAgentTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLAgentTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    /**
     * Creates attributes for an agent task span.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @return A map of attributes for the agent task span.
     */
    public static Map<String, String> createAgentTaskAttributes(String agentName, String userTask) {
        return createAgentTaskAttributes(agentName, userTask, null, null);
    }

    /**
     * Creates attributes for an agent task span.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @param agentId The agent id.
     * @param modelId The model id.
     * @return A map of attributes for the agent task span.
     */
    public static Map<String, String> createAgentTaskAttributes(String agentName, String userTask, String agentId, String modelId) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        if (agentName != null && !agentName.isEmpty()) {
            attributes.put(ATTR_NAME, agentName);
        }
        if (userTask != null && !userTask.isEmpty()) {
            attributes.put(ATTR_TASK, userTask);
        }
        if (agentId != null && !agentId.isEmpty()) {
            attributes.put(ATTR_AGENT_ID, agentId);
        }
        if (modelId != null && !modelId.isEmpty()) {
            attributes.put(ATTR_MODEL_ID, modelId);
        }
        attributes.put(ATTR_OPERATION_NAME, OperationType.CREATE_AGENT.getValue());
        return attributes;
    }

    /**
     * Creates attributes for a plan step span.
     * @param stepNumber The step number in the plan.
     * @return A map of attributes for the plan step span.
     */
    public static Map<String, String> createPlanAttributes(int stepNumber) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        attributes.put(ATTR_PHASE, PhaseType.PLANNER.getValue());
        attributes.put(ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        attributes.put(ATTR_OPERATION_NAME, OperationType.CREATE_AGENT.getValue());
        // TODO: get LLM system and model
        return attributes;
    }

    /**
     * Creates attributes for an execute step span.
     * @param stepNumber The step number in the execution.
     * @return A map of attributes for the execute step span.
     */
    public static Map<String, String> createExecuteStepAttributes(int stepNumber) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        attributes.put(ATTR_PHASE, PhaseType.EXECUTOR.getValue());
        attributes.put(ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        attributes.put(ATTR_OPERATION_NAME, OperationType.INVOKE_AGENT.getValue());
        return attributes;
    }

    /**
     * Creates attributes for an LLM call span with pre-extracted tokens.
     * @param completion The completion string from the LLM.
     * @param latency The latency of the LLM call.
     * @param parameters The parameters used for the LLM call.
     * @param extractedTokens Pre-extracted token information to avoid duplication.
     * @return A map of attributes for the LLM call span.
     */
    public static Map<String, String> createLLMCallAttributes(
        String completion,
        long latency,
        Map<String, String> parameters,
        Map<String, Integer> extractedTokens
    ) {
        Map<String, String> attributes = new HashMap<>();

        String provider = detectProviderFromParameters(parameters);
        attributes.put(ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        attributes.put(ATTR_SYSTEM, provider);
        // TODO: get actual request model
        attributes.put(ATTR_OPERATION_NAME, OperationType.CHAT.getValue());
        if (parameters.containsKey(PARAM_PROMPT)) {
            attributes.put(ATTR_TASK, parameters.get(PARAM_PROMPT));
        }
        if (completion != null) {
            attributes.put(ATTR_RESULT, completion);
        }
        attributes.put(ATTR_LATENCY, String.valueOf(latency));
        attributes.put(ATTR_PHASE, PhaseType.PLANNER.getValue());
        if (parameters.containsKey(PARAM_SYSTEM_PROMPT)) {
            attributes.put(ATTR_SYSTEM_MESSAGE, parameters.get(PARAM_SYSTEM_PROMPT));
        }
        if (parameters.containsKey(PARAM_TOOLS_PROMPT)) {
            attributes.put(ATTR_TOOL_DESCRIPTION, parameters.get(PARAM_TOOLS_PROMPT));
        }

        if (extractedTokens != null && !extractedTokens.isEmpty()) {
            if (extractedTokens.containsKey(TOKEN_FIELD_INPUT_TOKENS)) {
                attributes.put(ATTR_USAGE_INPUT_TOKENS, String.valueOf(extractedTokens.get(TOKEN_FIELD_INPUT_TOKENS).intValue()));
            }
            if (extractedTokens.containsKey(TOKEN_FIELD_OUTPUT_TOKENS)) {
                attributes.put(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(extractedTokens.get(TOKEN_FIELD_OUTPUT_TOKENS).intValue()));
            }
            if (extractedTokens.containsKey(TOKEN_FIELD_TOTAL_TOKENS)) {
                attributes.put(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(extractedTokens.get(TOKEN_FIELD_TOTAL_TOKENS).intValue()));
            }
        }

        return attributes;
    }

    /**
     * Detects the provider from the parameters map using the shared utility from MLModel.
     * @param parameters The parameters map.
     * @return The provider string (e.g., "openai", "aws.bedrock", etc.)
     */
    public static String detectProviderFromParameters(Map<String, String> parameters) {
        String llmInterface = parameters.getOrDefault(PARAM_LLM_INTERFACE, "");
        return llmInterface.isEmpty() ? PROVIDER_UNKNOWN : MLModel.identifyServiceProviderFromUrl(llmInterface.toLowerCase());
    }

    public static Map<String, String> createToolCallAttributesWithStep(
        String actionInput,
        int stepNumber,
        String toolName,
        String toolDescription
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(MLAgentTracer.ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        attributes.put(MLAgentTracer.ATTR_OPERATION_NAME, OperationType.EXECUTE_TOOL.getValue());
        attributes.put(MLAgentTracer.ATTR_TASK, actionInput != null ? actionInput : "");
        attributes.put(MLAgentTracer.ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        attributes.put(MLAgentTracer.ATTR_TOOL_NAME, toolName != null ? toolName : "");
        if (toolDescription != null) {
            attributes.put(MLAgentTracer.ATTR_TOOL_DESCRIPTION, toolDescription);
        }
        return attributes;
    }

    public static Map<String, String> createLLMCallAttributesForConv(
        String question,
        int stepNumber,
        String systemPrompt,
        String llmInterface
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(MLAgentTracer.ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        attributes.put(MLAgentTracer.ATTR_OPERATION_NAME, OperationType.CHAT.getValue());
        attributes.put(MLAgentTracer.ATTR_TASK, question != null ? question : "");
        attributes.put(MLAgentTracer.ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        if (systemPrompt != null) {
            attributes.put(MLAgentTracer.ATTR_SYSTEM_MESSAGE, systemPrompt);
        }
        if (llmInterface != null) {
            String provider = MLModel.identifyServiceProviderFromUrl(llmInterface.toLowerCase());
            attributes.put(MLAgentTracer.ATTR_SYSTEM, provider);
        }
        return attributes;
    }

    /**
     * Container for tool call extraction results, including input, output, usage, and metrics.
     */
    public static class ToolCallExtractionResult {
        public String input;
        public String output = "";
        public Map<String, Object> usage;
        public Map<String, Object> metrics;
    }

    /**
     * Extracts tool call information from the given tool output and action input.
     * @param toolOutput The tool output object (e.g., ModelTensorOutput).
     * @param actionInput The action input string.
     * @return A ToolCallExtractionResult containing extracted input, output, usage, and metrics.
     */
    public static ToolCallExtractionResult extractToolCallInfo(Object toolOutput, String actionInput) {
        ToolCallExtractionResult result = new ToolCallExtractionResult();
        result.input = actionInput;

        if (!(toolOutput instanceof ModelTensorOutput)) {
            result.output = toolOutput != null ? toolOutput.toString() : "";
            return result;
        }
        ModelTensorOutput mto = (ModelTensorOutput) toolOutput;
        if (mto.getMlModelOutputs() == null || mto.getMlModelOutputs().isEmpty())
            return result;
        List<ModelTensor> tensors = mto.getMlModelOutputs().get(0).getMlModelTensors();
        if (tensors == null || tensors.isEmpty()) {
            return result;
        }
        ModelTensor tensor = tensors.get(0);
        // Try result
        if (tensor.getResult() != null) {
            result.output = tensor.getResult();
        }
        // Try dataAsMap
        Map<String, ?> map = null;
        try {
            map = tensor.getDataAsMap();
        } catch (Exception e) {
            log.warn("[AGENT_TRACE] Exception getting dataAsMap from tensor: {}", e.getMessage());
        }

        if (map == null && result.output.isEmpty()) {
            result.output = tensor.toString();
            log.warn("[AGENT_TRACE] tensor.getDataAsMap() is null; using tensor.toString() as output");
            return result;
        }
        if (map == null) {
            return result;
        }
        if (map.containsKey(RESPONSE_FIELD)) {
            Object resp = map.get(RESPONSE_FIELD);
            result.output = (resp instanceof String) ? (String) resp : StringUtils.toJson(resp);
        } else if (map.containsKey(OUTPUT_FIELD)) {
            Object out = map.get(OUTPUT_FIELD);
            result.output = (out instanceof String) ? (String) out : StringUtils.toJson(out);
        } else if (result.output.isEmpty() && !map.isEmpty()) {
            Object firstValue = map.values().iterator().next();
            result.output = (firstValue instanceof String) ? (String) firstValue : StringUtils.toJson(firstValue);
        }

        if (map.containsKey(USAGE_FIELD)) {
            Object usageObj = map.get(USAGE_FIELD);
            if (usageObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMap = (Map<String, Object>) usageObj;
                result.usage = usageMap;
            }
        }
        if (map.containsKey(METRICS_FIELD)) {
            Object metricsObj = map.get(METRICS_FIELD);
            if (metricsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metricsMap = (Map<String, Object>) metricsObj;
                result.metrics = metricsMap;
            }
        }

        return result;
    }

    public static void updateToolCallSpanWithResult(Span span, ToolCallExtractionResult result) {
        Integer inputTokens = extractTokenValue(result.usage, TOKEN_FIELD_INPUT_TOKENS);
        Integer outputTokens = extractTokenValue(result.usage, TOKEN_FIELD_OUTPUT_TOKENS);
        Integer totalTokens = extractTokenValue(result.usage, TOKEN_FIELD_TOTAL_TOKENS);
        Integer latency = extractTokenValue(result.metrics, METRIC_FIELD_LATENCY_MS);
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(span, null);
        context.getCurrentResult().set(result.output);
        if (latency != null) {
            context.getCurrentLatency().set(latency.longValue());
        }
        if (inputTokens != null) {
            context.getPhaseInputTokens().set(inputTokens);
        }
        if (outputTokens != null) {
            context.getPhaseOutputTokens().set(outputTokens);
        }
        if (totalTokens != null) {
            context.getPhaseTotalTokens().set(totalTokens);
        }
        MLAgentTracer.updateSpanWithResultAttributes(span, context);
    }

    /**
     * Updates the given span with result attributes such as result, input tokens, output tokens, total tokens, and latency.
     * @param span The span to update.
     * @param context The agent execution context containing token references, result, and latency.
     */
    public static void updateSpanWithResultAttributes(Span span, AgentExecutionContext context) {
        if (span == null)
            return;
        if (!context.getCurrentResult().get().isEmpty()) {
            span.addAttribute(ATTR_RESULT, context.getCurrentResult().get());
        }
        if (context.getPhaseInputTokens().get() > 0) {
            span.addAttribute(ATTR_USAGE_INPUT_TOKENS, String.valueOf(context.getPhaseInputTokens().get()));
        }
        if (context.getPhaseOutputTokens().get() > 0) {
            span.addAttribute(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(context.getPhaseOutputTokens().get()));
        }
        if (context.getPhaseTotalTokens().get() > 0) {
            span.addAttribute(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(context.getPhaseTotalTokens().get()));
        }
        if (context.getCurrentLatency().get() > 0L) {
            span.addAttribute(ATTR_LATENCY, String.valueOf(context.getCurrentLatency().get().intValue()));
        }
    }

    /**
     * Updates the agent task span with cumulative agent token totals.
     * @param context The agent execution context containing agent token references.
     */
    public static void updateAgentTaskSpanWithCumulativeTokens(AgentExecutionContext context) {
        if (context.getAgentTaskSpan() == null)
            return;
        if (context.getAgentInputTokens().get() > 0) {
            context.getAgentTaskSpan().addAttribute(ATTR_USAGE_INPUT_TOKENS, String.valueOf(context.getAgentInputTokens().get()));
        }
        if (context.getAgentOutputTokens().get() > 0) {
            context.getAgentTaskSpan().addAttribute(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(context.getAgentOutputTokens().get()));
        }
        if (context.getAgentTotalTokens().get() > 0) {
            context.getAgentTaskSpan().addAttribute(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(context.getAgentTotalTokens().get()));
        }
    }

    /**
     * Encapsulates all span-related attributes and token tracking for agent execution.
     */
    public static class AgentExecutionContext {
        private final AtomicReference<Integer> agentInputTokens;
        private final AtomicReference<Integer> agentOutputTokens;
        private final AtomicReference<Integer> agentTotalTokens;
        private final AtomicReference<Integer> phaseInputTokens;
        private final AtomicReference<Integer> phaseOutputTokens;
        private final AtomicReference<Integer> phaseTotalTokens;
        private final AtomicReference<String> currentResult;
        private final AtomicReference<Long> currentLatency;
        private final AtomicReference<Integer> llmCallIndex;
        private final AtomicReference<Integer> toolCallIndex;
        private final Span agentTaskSpan;
        private final Span planSpan;

        public AgentExecutionContext(Span agentTaskSpan, Span planSpan) {
            this.agentInputTokens = new AtomicReference<>(0);
            this.agentOutputTokens = new AtomicReference<>(0);
            this.agentTotalTokens = new AtomicReference<>(0);
            this.phaseInputTokens = new AtomicReference<>(0);
            this.phaseOutputTokens = new AtomicReference<>(0);
            this.phaseTotalTokens = new AtomicReference<>(0);
            this.currentResult = new AtomicReference<>("");
            this.currentLatency = new AtomicReference<>(0L);
            this.llmCallIndex = new AtomicReference<>(0);
            this.toolCallIndex = new AtomicReference<>(0);
            this.agentTaskSpan = agentTaskSpan;
            this.planSpan = planSpan;
        }

        public AtomicReference<Integer> getAgentInputTokens() {
            return agentInputTokens;
        }

        public AtomicReference<Integer> getAgentOutputTokens() {
            return agentOutputTokens;
        }

        public AtomicReference<Integer> getAgentTotalTokens() {
            return agentTotalTokens;
        }

        public AtomicReference<Integer> getPhaseInputTokens() {
            return phaseInputTokens;
        }

        public AtomicReference<Integer> getPhaseOutputTokens() {
            return phaseOutputTokens;
        }

        public AtomicReference<Integer> getPhaseTotalTokens() {
            return phaseTotalTokens;
        }

        public AtomicReference<String> getCurrentResult() {
            return currentResult;
        }

        public AtomicReference<Long> getCurrentLatency() {
            return currentLatency;
        }

        public Span getAgentTaskSpan() {
            return agentTaskSpan;
        }

        public Span getPlanSpan() {
            return planSpan;
        }

        public AtomicReference<Integer> getLlmCallIndex() {
            return llmCallIndex;
        }

        public AtomicReference<Integer> getToolCallIndex() {
            return toolCallIndex;
        }
    }

    /**
     * Initializes phase tokens to 0.
     * @param context The agent execution context containing all token references.
     */
    public static void initPhaseTokens(AgentExecutionContext context) {
        context.getPhaseInputTokens().set(0);
        context.getPhaseOutputTokens().set(0);
        context.getPhaseTotalTokens().set(0);
    }

    /**
     * Initializes phase tokens with extracted token values using context.
     * @param context The agent execution context containing phase token references.
     * @param extractedTokens The map containing extracted token values.
     */
    public static void initPhaseTokensWithExtractedValues(AgentExecutionContext context, Map<String, Integer> extractedTokens) {
        context.getPhaseInputTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_INPUT_TOKENS, 0));
        context.getPhaseOutputTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_OUTPUT_TOKENS, 0));
        context.getPhaseTotalTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_TOTAL_TOKENS, 0));
    }

    /**
     * Sets the current result and latency in the context.
     * @param context The agent execution context.
     * @param result The result string.
     * @param latency The latency value.
     */
    public static void setCurrentResultAndLatency(AgentExecutionContext context, String result, Long latency) {
        context.getCurrentResult().set(result);
        context.getCurrentLatency().set(latency);
    }

    /**
     * Increments phase tokens with values from ReAct agent output.
     * @param reactResult The ReAct agent result to extract tokens from.
     * @param context The agent execution context containing all token references.
     */
    public static void incrementPhaseTokensFromReActOutput(ModelTensorOutput reactResult, AgentExecutionContext context) {
        if (reactResult == null || reactResult.getMlModelOutputs() == null || reactResult.getMlModelOutputs().isEmpty()) {
            return;
        }

        List<ModelTensor> tensors = reactResult.getMlModelOutputs().getLast().getMlModelTensors();
        if (tensors == null || tensors.isEmpty()) {
            return;
        }

        Map<String, ?> dataMap = tensors.getLast().getDataAsMap();
        if (dataMap == null) {
            return;
        }

        // Extract tokens from additional_info or additionalInfo
        Object addInfoObj = dataMap.get(ADDITIONAL_INFO_FIELD);
        if (addInfoObj == null) {
            addInfoObj = dataMap.get(ADDITIONAL_INFO_FIELD_ALT);
        }

        if (addInfoObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> addInfo = (Map<String, Object>) addInfoObj;
            Integer execInput = extractTokenValue(addInfo, TOKEN_FIELD_INPUT_TOKENS);
            Integer execOutput = extractTokenValue(addInfo, TOKEN_FIELD_OUTPUT_TOKENS);
            Integer execTotal = extractTokenValue(addInfo, TOKEN_FIELD_TOTAL_TOKENS);

            if (execInput != null) {
                context.getPhaseInputTokens().set(context.getPhaseInputTokens().get() + execInput);
            }
            if (execOutput != null) {
                context.getPhaseOutputTokens().set(context.getPhaseOutputTokens().get() + execOutput);
            }
            if (execTotal != null) {
                context.getPhaseTotalTokens().set(context.getPhaseTotalTokens().get() + execTotal);
            }
        }
    }

    /**
     * Accumulates phase tokens to agent token totals.
     * @param context The agent execution context containing all token references.
     */
    public static void accumulateTokensToAgent(AgentExecutionContext context) {
        context.getAgentInputTokens().set(context.getAgentInputTokens().get() + context.getPhaseInputTokens().get());
        context.getAgentOutputTokens().set(context.getAgentOutputTokens().get() + context.getPhaseOutputTokens().get());
        context.getAgentTotalTokens().set(context.getAgentTotalTokens().get() + context.getPhaseTotalTokens().get());
    }

    /**
     * Processes ReAct agent execution results and updates all spans and token tracking.
     * This method combines multiple operations: initializing phase tokens, incrementing from ReAct output,
     * setting span task and result, updating span attributes, accumulating tokens to agent,
     * and ending the execute step span.
     * 
     * @param executeStepSpan The execute step span to update and end.
     * @param reactResult The ReAct agent result to extract tokens from.
     * @param stepToExecute The step that was executed.
     * @param stepResult The result of the step execution.
     * @param context The agent execution context containing all token references.
     */
    public static void processReActExecutionResults(
        Span executeStepSpan,
        ModelTensorOutput reactResult,
        String stepToExecute,
        String stepResult,
        AgentExecutionContext context
    ) {
        initPhaseTokens(context);
        incrementPhaseTokensFromReActOutput(reactResult, context);
        setSpanTaskAndResult(executeStepSpan, stepToExecute, stepResult);
        updateSpanWithResultAttributes(executeStepSpan, context);
        accumulateTokensToAgent(context);
        updateAgentTaskSpanWithCumulativeTokens(context);
        initPhaseTokens(context);
        getInstance().endSpan(executeStepSpan);
    }

    /**
     * Processes planning phase results and updates all spans and token tracking.
     * This method combines multiple operations: setting span task and result,
     * updating span attributes, accumulating tokens to agent, and ending the plan step span.
     * 
     * @param planStepSpan The plan step span to update and end.
     * @param prompt The prompt that was used for planning.
     * @param completion The completion string from the planning.
     * @param context The agent execution context containing all token references.
     */
    public static void processPlanningResults(Span planStepSpan, String prompt, String completion, AgentExecutionContext context) {
        setSpanTaskAndResult(planStepSpan, prompt, completion);
        updateSpanWithResultAttributes(planStepSpan, context);
        accumulateTokensToAgent(context);
        updateAgentTaskSpanWithCumulativeTokens(context);
        getInstance().endSpan(planStepSpan);
    }

    /**
     * Processes LLM call results and updates the LLM call span.
     * This method combines multiple operations: setting current result and latency in context,
     * creating LLM call attributes, setting span attributes, and ending the LLM call span.
     * 
     * @param llmCallSpan The LLM call span to update and end.
     * @param completion The completion string from the LLM.
     * @param llmLatency The latency of the LLM call.
     * @param allParams The parameters used for the LLM call.
     * @param extractedTokens The extracted token information.
     * @param context The agent execution context containing all token references.
     */
    public static void processLLMCallResults(
        Span llmCallSpan,
        String completion,
        long llmLatency,
        Map<String, String> allParams,
        Map<String, Integer> extractedTokens,
        AgentExecutionContext context
    ) {
        setCurrentResultAndLatency(context, completion, llmLatency);
        Map<String, String> updatedLLMCallAttrs = createLLMCallAttributes(completion, llmLatency, allParams, extractedTokens);
        setSpanAttributes(llmCallSpan, updatedLLMCallAttrs);
        getInstance().endSpan(llmCallSpan);
    }

    /**
     * Processes LLM tool call extraction and updates all related spans and token tracking.
     * This method combines multiple operations: extracting tool call info, accumulating tokens,
     * and updating and ending LLM span.
     * 
     * @param tmpModelTensorOutput The model tensor output from LLM.
     * @param context The agent execution context.
     * @param lastLlmListenerWithSpan The listener with span to update.
     * @return The extracted tool call result.
     */
    public static ToolCallExtractionResult processLLMToolCall(
        ModelTensorOutput tmpModelTensorOutput,
        AgentExecutionContext context,
        ListenerWithSpan lastLlmListenerWithSpan
    ) {
        ToolCallExtractionResult llmResultInfo = extractToolCallInfo(tmpModelTensorOutput, null);
        extractAndAccumulateTokensToAgent(llmResultInfo.usage, context);
        updateAndEndLLMSpan(lastLlmListenerWithSpan, llmResultInfo, context);
        return llmResultInfo;
    }

    /**
     * Processes final answer with comprehensive tracing updates.
     * This method combines multiple operations: updating and ending LLM span,
     * updating agent task span with cumulative tokens, and updating additional info with tokens.
     * 
     * @param lastLlmListenerWithSpan The listener with span to update.
     * @param llmResultInfo The LLM result info.
     * @param context The agent execution context.
     * @param additionalInfo The additional info map to update with tokens.
     */
    public static void processFinalAnswer(
        ListenerWithSpan lastLlmListenerWithSpan,
        ToolCallExtractionResult llmResultInfo,
        AgentExecutionContext context,
        Map<String, Object> additionalInfo
    ) {
        updateAndEndLLMSpan(lastLlmListenerWithSpan, llmResultInfo, context);
        updateAgentTaskSpanWithCumulativeTokens(context);
        updateAdditionalInfoWithTokens(context, additionalInfo);
    }

    /**
     * Creates and connects an LLM listener with span for the next iteration.
     * This method combines listener creation, span association, and connection to next step listener.
     * 
     * @param nextLlmCallSpan The next LLM call span.
     * @param nextStepListener The next step listener to connect to.
     * @param lastLlmListenerWithSpan The atomic reference to update with the new listener.
     * @return The created LLM listener.
     */
    public static StepListener<MLTaskResponse> createAndConnectLLMListener(
        Span nextLlmCallSpan,
        StepListener<MLTaskResponse> nextStepListener,
        AtomicReference<ListenerWithSpan> lastLlmListenerWithSpan
    ) {
        StepListener<MLTaskResponse> llmListener = new StepListener<>();
        ListenerWithSpan llmListenerWithSpan = new ListenerWithSpan(llmListener, nextLlmCallSpan);
        lastLlmListenerWithSpan.set(llmListenerWithSpan);

        if (nextStepListener != null) {
            llmListener.whenComplete(response -> { nextStepListener.onResponse(response); }, e -> { nextStepListener.onFailure(e); });
        }

        return llmListener;
    }

    /**
     * Increments the appropriate index based on the iteration number.
     * This method handles the alternating pattern of LLM calls and tool calls.
     * 
     * @param context The agent execution context.
     * @param iterationNumber The current iteration number.
     */
    public static void incrementIndexForIteration(AgentExecutionContext context, int iterationNumber) {
        if (iterationNumber % 2 == 0) {
            context.getLlmCallIndex().set(context.getLlmCallIndex().get() + 1);
        } else {
            context.getToolCallIndex().set(context.getToolCallIndex().get() + 1);
        }
    }

    /**
     * Starts an agent task span with the given agent name and user task.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @param agentId The agent id.
     * @param modelId The model id.
     * @return The started Span.
     */
    public Span startAgentTaskSpan(String agentName, String userTask, String agentId, String modelId) {
        return startSpan(AGENT_TASK_PER_SPAN, createAgentTaskAttributes(agentName, userTask, agentId, modelId));
    }

    /**
     * Starts a plan or reflect step span based on the step number.
     * If stepsExecuted is 0, uses AGENT_PLAN_SPAN, otherwise uses AGENT_REFLECT_STEP_SPAN with step number.
     * @param stepsExecuted The step number in the plan/reflect phase.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startPlanOrReflectStepSpan(int stepsExecuted, Span parentSpan) {
        String spanName = stepsExecuted == 0 ? AGENT_PLAN_SPAN : String.format(FORMAT_REFLECT_STEP_SPAN, stepsExecuted);
        return startSpan(spanName, createPlanAttributes(stepsExecuted), parentSpan);
    }

    /**
     * Starts an execute step span with the given step number and parent span.
     * @param stepNumber The step number in the execution.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startExecuteStepSpan(int stepNumber, Span parentSpan) {
        return startSpan(String.format(FORMAT_EXECUTE_STEP_SPAN, stepNumber), createExecuteStepAttributes(stepNumber), parentSpan);
    }

    /**
     * Starts an LLM call span with the given parameters and parent span.
     * @param completion The completion string from the LLM.
     * @param latency The latency of the LLM call.
     * @param modelTensorOutput The model tensor output.
     * @param parameters The parameters used for the LLM call.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startLLMCallSpan(
        String completion,
        long latency,
        Map<String, String> parameters,
        Map<String, Integer> extractedTokens,
        Span parentSpan
    ) {
        return startSpan(AGENT_LLM_CALL_SPAN, createLLMCallAttributes(completion, latency, parameters, extractedTokens), parentSpan);
    }

    /**
     * Starts a conversational agent task span with the given agent name and user task.
     * If the conversational agent is run independently, it will start a new span.
     * If the conversational agent is run through another agent, it will use the parent span context.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @param inputParams The input parameters.
     * @param hasParentSpanContext Whether the conversational agent is run through another agent.
     * @return The started Span.
     */
    public Span startConversationalAgentTaskSpanLogic(String agentName, Map<String, String> inputParams, String agentId, String modelId) {
        // Check if conversational is run independently or through another agent
        boolean hasParentSpanContext = inputParams.containsKey(TRACE_PARENT_FIELD);
        String userTask = inputParams.get(QUESTION_FIELD);
        final Span agentTaskSpan;
        if (hasParentSpanContext) {
            Span parentSpan = MLAgentTracer.getInstance().extractSpanContext(inputParams);
            agentTaskSpan = MLAgentTracer.getInstance().startConversationalAgentTaskSpan(agentName, userTask, agentId, modelId, parentSpan);
        } else {
            agentTaskSpan = MLAgentTracer.getInstance().startConversationalAgentTaskSpan(agentName, userTask, agentId, modelId);
        }
        return agentTaskSpan;
    }

    /**
     * Starts a conversational agent task span with the given agent name and user task.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @param agentId The agent id.
     * @param modelId The model id.
     * @return The started Span.
     */
    public Span startConversationalAgentTaskSpan(String agentName, String userTask, String agentId, String modelId) {
        return startSpan(AGENT_TASK_CONV_SPAN, createAgentTaskAttributes(agentName, userTask, agentId, modelId));
    }

    /**
     * Starts a conversational agent task span with parent span context.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @param agentId The agent id.
     * @param modelId The model id.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startConversationalAgentTaskSpan(String agentName, String userTask, String agentId, String modelId, Span parentSpan) {
        return startSpan(AGENT_CONV_TASK_SPAN, createAgentTaskAttributes(agentName, userTask, agentId, modelId), parentSpan);
    }

    /**
     * Starts an LLM call span for conversational agent.
     * @param question The question being asked.
     * @param stepNumber The step number in the conversation.
     * @param systemPrompt The system prompt.
     * @param llmInterface The LLM interface.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startConversationalLLMCallSpan(String question, int stepNumber, String systemPrompt, String llmInterface, Span parentSpan) {
        return startSpan(
            AGENT_LLM_CALL_SPAN + "_" + stepNumber,
            createLLMCallAttributesForConv(question, stepNumber, systemPrompt, llmInterface),
            parentSpan
        );
    }

    /**
     * Starts a tool call span for conversational agent.
     * @param actionInput The action input.
     * @param stepNumber The step number in the conversation.
     * @param toolName The tool name.
     * @param toolDescription The tool description.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startConversationalToolCallSpan(
        String actionInput,
        int stepNumber,
        String toolName,
        String toolDescription,
        Span parentSpan
    ) {
        return startSpan(
            AGENT_TOOL_CALL_SPAN + "_" + stepNumber,
            createToolCallAttributesWithStep(actionInput, stepNumber, toolName, toolDescription),
            parentSpan
        );
    }

    /**
     * Starts a conversational flow agent task span with the given agent name and user task.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @return The started Span.
     */
    public Span startConversationalFlowAgentTaskSpan(String agentName, String userTask) {
        return startSpan(AGENT_TASK_CONV_FLOW_SPAN, createAgentTaskAttributes(agentName, userTask));
    }

    /**
     * Starts a flow agent task span with the given agent name and user task.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @return The started Span.
     */
    public Span startFlowAgentTaskSpan(String agentName, String userTask) {
        return startSpan(AGENT_TASK_FLOW_SPAN, createAgentTaskAttributes(agentName, userTask, null, null));
    }

    /**
     * Extracts token information from ModelTensorOutput using the robust provider-specific logic.
     * @param modelTensorOutput The model output to extract tokens from.
     * @param parameters The parameters used for the LLM call (for provider detection).
     * @return A map of extracted token information.
     */
    public static Map<String, Integer> extractTokensFromModelOutput(ModelTensorOutput modelTensorOutput, Map<String, String> parameters) {
        Map<String, Integer> extractedTokens = new HashMap<>();
        if (modelTensorOutput == null || modelTensorOutput.getMlModelOutputs() == null || modelTensorOutput.getMlModelOutputs().isEmpty()) {
            return extractedTokens;
        }
        String provider = MLAgentTracer.detectProviderFromParameters(parameters);
        for (ModelTensors output : modelTensorOutput.getMlModelOutputs()) {
            if (output.getMlModelTensors() != null) {
                for (ModelTensor tensor : output.getMlModelTensors()) {
                    processTensorForTokens(tensor, provider, extractedTokens);
                }
            }
        }
        return extractedTokens;
    }

    private static void processTensorForTokens(ModelTensor tensor, String provider, Map<String, Integer> extractedTokens) {
        Map<String, ?> dataAsMap = tensor.getDataAsMap();
        if (dataAsMap == null || !dataAsMap.containsKey(USAGE_FIELD)) {
            return;
        }
        Object usageObj = dataAsMap.get(USAGE_FIELD);
        if (!(usageObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) usageObj;

        if (provider.toLowerCase().contains(PROVIDER_BEDROCK)) {
            extractBedrockTokens(usage, extractedTokens);
        } else if (provider.toLowerCase().contains(PROVIDER_OPENAI)) {
            extractOpenAITokens(usage, extractedTokens);
        } else {
            // TODO: find general method for all providers
        }
    }

    /**
     * Extracts token information from Bedrock usage.
     * @param usage The usage map from Bedrock.
     * @param extractedTokens The map to store extracted tokens.
     */
    private static void extractBedrockTokens(Map<String, Object> usage, Map<String, Integer> extractedTokens) {
        Object inputTokens = null;
        Object outputTokens = null;
        for (String key : usage.keySet()) {
            if (key.equalsIgnoreCase(TOKEN_FIELD_INPUT_TOKENS_ALT) || key.equalsIgnoreCase(TOKEN_FIELD_INPUT_TOKENS)) {
                inputTokens = usage.get(key);
            }
            if (key.equalsIgnoreCase(TOKEN_FIELD_OUTPUT_TOKENS_ALT) || key.equalsIgnoreCase(TOKEN_FIELD_OUTPUT_TOKENS)) {
                outputTokens = usage.get(key);
            }
        }

        Integer inputTokensValue = null;
        Integer outputTokensValue = null;
        try {
            if (inputTokens != null) {
                inputTokensValue = inputTokens instanceof Number
                    ? ((Number) inputTokens).intValue()
                    : Integer.parseInt(inputTokens.toString());
            }
            if (outputTokens != null) {
                outputTokensValue = outputTokens instanceof Number
                    ? ((Number) outputTokens).intValue()
                    : Integer.parseInt(outputTokens.toString());
            }
        } catch (NumberFormatException e) {
            log
                .warn(
                    "[AGENT_TRACE] Failed to parse Bedrock token values: inputTokens={}, outputTokens={}, error={}",
                    inputTokens,
                    outputTokens,
                    e.getMessage()
                );
        }

        if (inputTokensValue != null) {
            extractedTokens.put(TOKEN_FIELD_INPUT_TOKENS, inputTokensValue);
        }
        if (outputTokensValue != null) {
            extractedTokens.put(TOKEN_FIELD_OUTPUT_TOKENS, outputTokensValue);
        }
        if (inputTokensValue != null && outputTokensValue != null) {
            int totalTokens = inputTokensValue + outputTokensValue;
            extractedTokens.put(TOKEN_FIELD_TOTAL_TOKENS, totalTokens);
        }
    }

    /**
     * Extracts token information from OpenAI usage.
     * @param usage The usage map from OpenAI.
     * @param extractedTokens The map to store extracted tokens.
     */
    private static void extractOpenAITokens(Map<String, Object> usage, Map<String, Integer> extractedTokens) {
        Object promptTokens = null;
        Object completionTokens = null;
        Object totalTokens = null;
        for (String key : usage.keySet()) {
            if (key.equalsIgnoreCase(TOKEN_FIELD_PROMPT_TOKENS)) {
                promptTokens = usage.get(key);
            }
            if (key.equalsIgnoreCase(TOKEN_FIELD_COMPLETION_TOKENS)) {
                completionTokens = usage.get(key);
            }
            if (key.equalsIgnoreCase(TOKEN_FIELD_TOTAL_TOKENS_ALT)) {
                totalTokens = usage.get(key);
            }
        }

        if (promptTokens != null) {
            try {
                extractedTokens
                    .put(
                        TOKEN_FIELD_INPUT_TOKENS,
                        promptTokens instanceof Number ? ((Number) promptTokens).intValue() : Integer.parseInt(promptTokens.toString())
                    );
            } catch (NumberFormatException e) {
                log.warn("[AGENT_TRACE] Failed to parse OpenAI prompt tokens: promptTokens={}, error={}", promptTokens, e.getMessage());
            }
        }
        if (completionTokens != null) {
            try {
                extractedTokens
                    .put(
                        TOKEN_FIELD_OUTPUT_TOKENS,
                        completionTokens instanceof Number
                            ? ((Number) completionTokens).intValue()
                            : Integer.parseInt(completionTokens.toString())
                    );
            } catch (NumberFormatException e) {
                log
                    .warn(
                        "[AGENT_TRACE] Failed to parse OpenAI completion tokens: completionTokens={}, error={}",
                        completionTokens,
                        e.getMessage()
                    );
            }
        }
        if (totalTokens != null) {
            try {
                extractedTokens
                    .put(
                        TOKEN_FIELD_TOTAL_TOKENS,
                        totalTokens instanceof Number ? ((Number) totalTokens).intValue() : Integer.parseInt(totalTokens.toString())
                    );
            } catch (NumberFormatException e) {
                log.warn("[AGENT_TRACE] Failed to parse OpenAI total tokens: totalTokens={}, error={}", totalTokens, e.getMessage());
            }
        }
    }

    /**
     * Extracts a token value from a usage map.
     * @param usage The usage map containing token information.
     * @param tokenKey The key to extract the token value for.
     * @return The extracted token value as an Integer, or null if not found or invalid.
     */
    public static Integer extractTokenValue(Map<String, Object> usage, String tokenKey) {
        if (usage == null || !usage.containsKey(tokenKey))
            return null;
        Object value = usage.get(tokenKey);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    /**
     * Extracts and accumulates token values to agent context.
     * @param usage The usage map containing token information.
     * @param context The agent execution context to update.
     */
    public static void extractAndAccumulateTokensToAgent(Map<String, Object> usage, AgentExecutionContext context) {
        if (usage == null)
            return;

        Integer inputTokens = extractTokenValue(usage, TOKEN_FIELD_INPUT_TOKENS);
        Integer outputTokens = extractTokenValue(usage, TOKEN_FIELD_OUTPUT_TOKENS);
        Integer totalTokens = extractTokenValue(usage, TOKEN_FIELD_TOTAL_TOKENS);

        if (inputTokens != null)
            context.getAgentInputTokens().set(context.getAgentInputTokens().get() + inputTokens);
        if (outputTokens != null)
            context.getAgentOutputTokens().set(context.getAgentOutputTokens().get() + outputTokens);
        if (totalTokens != null)
            context.getAgentTotalTokens().set(context.getAgentTotalTokens().get() + totalTokens);
    }

    /**
     * Extracts and sets token values to phase context.
     * @param usage The usage map containing token information.
     * @param context The agent execution context to update.
     */
    public static void extractAndSetPhaseTokens(Map<String, Object> usage, AgentExecutionContext context) {
        if (usage == null)
            return;

        Integer inputTokens = extractTokenValue(usage, TOKEN_FIELD_INPUT_TOKENS);
        Integer outputTokens = extractTokenValue(usage, TOKEN_FIELD_OUTPUT_TOKENS);
        Integer totalTokens = extractTokenValue(usage, TOKEN_FIELD_TOTAL_TOKENS);

        context.getPhaseInputTokens().set(inputTokens != null ? inputTokens : 0);
        context.getPhaseOutputTokens().set(outputTokens != null ? outputTokens : 0);
        context.getPhaseTotalTokens().set(totalTokens != null ? totalTokens : 0);
    }

    /**
     * Extracts latency from metrics and sets it in context.
     * @param metrics The metrics map containing latency information.
     * @param context The agent execution context to update.
     */
    public static void extractAndSetLatency(Map<String, Object> metrics, AgentExecutionContext context) {
        if (metrics == null)
            return;

        Integer latency = extractTokenValue(metrics, METRIC_FIELD_LATENCY_MS);
        if (latency != null) {
            context.getCurrentLatency().set(latency.longValue());
        }
    }

    /**
     * Updates LLM span with result and ends it.
     * @param currentLlmListenerWithSpan The listener with span to update.
     * @param llmResultInfo The LLM result information.
     * @param context The agent execution context.
     */
    public static void updateAndEndLLMSpan(
        ListenerWithSpan currentLlmListenerWithSpan,
        ToolCallExtractionResult llmResultInfo,
        AgentExecutionContext context
    ) {
        if (currentLlmListenerWithSpan == null || currentLlmListenerWithSpan.span == null)
            return;

        Span currentLlmSpan = currentLlmListenerWithSpan.span;
        context.getCurrentResult().set(llmResultInfo.output);

        extractAndSetPhaseTokens(llmResultInfo.usage, context);
        extractAndSetLatency(llmResultInfo.metrics, context);

        updateSpanWithResultAttributes(currentLlmSpan, context);
        setSpanResult(context.getAgentTaskSpan(), context.getCurrentResult().get());
        getInstance().endSpan(currentLlmSpan);
    }

    /**
     * Creates an MLTaskOutput with a simple response to avoid ByteBuffer serialization issues.
     * @param response The response string.
     * @return The MLTaskOutput.
     */
    public static MLTaskOutput createMLTaskOutput(String response) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("response", response);
        return MLTaskOutput.builder().taskId("tool_result").status("completed").response(responseMap).build();
    }

    /**
     * Updates additional info with token usage from context.
     * @param context The agent execution context.
     * @param additionalInfo The additional info map to update.
     */
    public static void updateAdditionalInfoWithTokens(AgentExecutionContext context, Map<String, Object> additionalInfo) {
        additionalInfo.put(TOKEN_FIELD_INPUT_TOKENS, context.getAgentInputTokens().get());
        additionalInfo.put(TOKEN_FIELD_OUTPUT_TOKENS, context.getAgentOutputTokens().get());
        additionalInfo.put(TOKEN_FIELD_TOTAL_TOKENS, context.getAgentTotalTokens().get());
    }

    /**
     * Handles span errors by logging, setting span error, and ending the span.
     * @param span The span to handle the error for.
     * @param errorMessage The error message to log.
     * @param e The exception that occurred.
     */
    public static void handleSpanError(Span span, String errorMessage, Exception e) {
        log.error(errorMessage, e);
        span.setError(e);
        getInstance().endSpan(span);
    }

    /**
     * Sets attributes on a span from a map of key-value pairs.
     * @param span The span to add attributes to.
     * @param attributes The map of attributes to add.
     */
    public static void setSpanAttributes(Span span, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            span.addAttribute(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sets the result attribute on a span.
     * @param span The span to set the result on.
     * @param result The result value to set.
     */
    public static void setSpanResult(Span span, String result) {
        span.addAttribute(ATTR_RESULT, result != null ? result : "");
    }

    /**
     * Sets the task attribute on a span.
     * @param span The span to set the task on.
     * @param task The task value to set.
     */
    public static void setSpanTask(Span span, String task) {
        if (task != null && !task.isEmpty()) {
            span.addAttribute(ATTR_TASK, task);
        }
    }

    /**
     * Sets both task and result attributes on a span.
     * @param span The span to set attributes on.
     * @param task The task value to set.
     * @param result The result value to set.
     */
    public static void setSpanTaskAndResult(Span span, String task, String result) {
        setSpanTask(span, task);
        setSpanResult(span, result);
    }

    /**
     * Updates the span with the tool call result.
     * @param toolCallSpan The span to update.
     * @param output The output of the tool call.
     * @param question The question that prompted the tool call.
     */
    public static void updateSpanWithTool(Span toolCallSpan, Object output, String question) {
        try {
            String outputResponse = parseResponse(output);
            toolCallSpan.addAttribute(ATTR_TASK, question);
            toolCallSpan.addAttribute(ATTR_RESULT, outputResponse);
            getInstance().endSpan(toolCallSpan);
        } catch (Exception e) {
            toolCallSpan.setError(e);
            getInstance().endSpan(toolCallSpan);
        }
    }

    /**
     * Parses the response object to extract the output string.
     * @param output The output object to parse.
     * @return The parsed response string.
     */
    private static String parseResponse(Object output) {
        if (output == null) {
            return "";
        }
        if (output instanceof String) {
            return (String) output;
        }
        if (output instanceof Map) {
            Map<?, ?> outputMap = (Map<?, ?>) output;
            Object response = outputMap.get(RESPONSE_FIELD);
            if (response != null) {
                return StringUtils.toJson(response);
            }
            Object result = outputMap.get(OUTPUT_FIELD);
            if (result != null) {
                return StringUtils.toJson(result);
            }
            return StringUtils.toJson(outputMap);
        }
        return output.toString();
    }

    /**
     * Encapsulates a listener with its associated span for tracking.
     */
    public record ListenerWithSpan(StepListener<MLTaskResponse> listener, Span span) {
    }

    /**
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    public static void resetForTest() {
        instance = null;
    }
}
