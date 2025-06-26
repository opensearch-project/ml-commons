/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
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
    public static final String PROVIDER_UNKNOWN = "unknown";

    public static final String TOKEN_FIELD_INPUT_TOKENS = "inputTokens";
    public static final String TOKEN_FIELD_OUTPUT_TOKENS = "outputTokens";
    public static final String TOKEN_FIELD_TOTAL_TOKENS = "totalTokens";
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
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTR_SERVICE_TYPE, SERVICE_TYPE_TRACER);
        if (agentName != null && !agentName.isEmpty()) {
            attributes.put(ATTR_NAME, agentName);
        }
        if (userTask != null && !userTask.isEmpty()) {
            attributes.put(ATTR_TASK, userTask);
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
        Map<String, Double> extractedTokens
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
        if (context.getPhaseInputTokens().get() > 0.0) {
            span.addAttribute(ATTR_USAGE_INPUT_TOKENS, String.valueOf(context.getPhaseInputTokens().get().intValue()));
        }
        if (context.getPhaseOutputTokens().get() > 0.0) {
            span.addAttribute(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(context.getPhaseOutputTokens().get().intValue()));
        }
        if (context.getPhaseTotalTokens().get() > 0.0) {
            span.addAttribute(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(context.getPhaseTotalTokens().get().intValue()));
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
        if (context.getAgentInputTokens().get() > 0.0) {
            context
                .getAgentTaskSpan()
                .addAttribute(ATTR_USAGE_INPUT_TOKENS, String.valueOf(context.getAgentInputTokens().get().intValue()));
        }
        if (context.getAgentOutputTokens().get() > 0.0) {
            context
                .getAgentTaskSpan()
                .addAttribute(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(context.getAgentOutputTokens().get().intValue()));
        }
        if (context.getAgentTotalTokens().get() > 0.0) {
            context
                .getAgentTaskSpan()
                .addAttribute(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(context.getAgentTotalTokens().get().intValue()));
        }
    }

    /**
     * Encapsulates all span-related attributes and token tracking for agent execution.
     */
    public static class AgentExecutionContext {
        private final AtomicReference<Double> agentInputTokens;
        private final AtomicReference<Double> agentOutputTokens;
        private final AtomicReference<Double> agentTotalTokens;
        private final AtomicReference<Double> phaseInputTokens;
        private final AtomicReference<Double> phaseOutputTokens;
        private final AtomicReference<Double> phaseTotalTokens;
        private final AtomicReference<String> currentResult;
        private final AtomicReference<Long> currentLatency;
        private final Span agentTaskSpan;
        private final Span planSpan;

        public AgentExecutionContext(Span agentTaskSpan, Span planSpan) {
            this.agentInputTokens = new AtomicReference<>(0.0);
            this.agentOutputTokens = new AtomicReference<>(0.0);
            this.agentTotalTokens = new AtomicReference<>(0.0);
            this.phaseInputTokens = new AtomicReference<>(0.0);
            this.phaseOutputTokens = new AtomicReference<>(0.0);
            this.phaseTotalTokens = new AtomicReference<>(0.0);
            this.currentResult = new AtomicReference<>("");
            this.currentLatency = new AtomicReference<>(0L);
            this.agentTaskSpan = agentTaskSpan;
            this.planSpan = planSpan;
        }

        public AtomicReference<Double> getAgentInputTokens() {
            return agentInputTokens;
        }

        public AtomicReference<Double> getAgentOutputTokens() {
            return agentOutputTokens;
        }

        public AtomicReference<Double> getAgentTotalTokens() {
            return agentTotalTokens;
        }

        public AtomicReference<Double> getPhaseInputTokens() {
            return phaseInputTokens;
        }

        public AtomicReference<Double> getPhaseOutputTokens() {
            return phaseOutputTokens;
        }

        public AtomicReference<Double> getPhaseTotalTokens() {
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
    }

    /**
     * Initializes phase tokens to 0.0.
     * @param context The agent execution context containing all token references.
     */
    public static void initPhaseTokens(AgentExecutionContext context) {
        context.getPhaseInputTokens().set(0.0);
        context.getPhaseOutputTokens().set(0.0);
        context.getPhaseTotalTokens().set(0.0);
    }

    /**
     * Initializes phase tokens with extracted token values using context.
     * @param context The agent execution context containing phase token references.
     * @param extractedTokens The map containing extracted token values.
     */
    public static void initPhaseTokensWithExtractedValues(AgentExecutionContext context, Map<String, Double> extractedTokens) {
        context.getPhaseInputTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_INPUT_TOKENS, 0.0));
        context.getPhaseOutputTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_OUTPUT_TOKENS, 0.0));
        context.getPhaseTotalTokens().set(extractedTokens.getOrDefault(TOKEN_FIELD_TOTAL_TOKENS, 0.0));
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
            Map<?, ?> addInfo = (Map<?, ?>) addInfoObj;
            Double execInput = addInfo.get(TOKEN_FIELD_INPUT_TOKENS) instanceof Number
                ? ((Number) addInfo.get(TOKEN_FIELD_INPUT_TOKENS)).doubleValue()
                : 0.0;
            Double execOutput = addInfo.get(TOKEN_FIELD_OUTPUT_TOKENS) instanceof Number
                ? ((Number) addInfo.get(TOKEN_FIELD_OUTPUT_TOKENS)).doubleValue()
                : 0.0;
            Double execTotal = addInfo.get(TOKEN_FIELD_TOTAL_TOKENS) instanceof Number
                ? ((Number) addInfo.get(TOKEN_FIELD_TOTAL_TOKENS)).doubleValue()
                : 0.0;

            context.getPhaseInputTokens().set(context.getPhaseInputTokens().get() + execInput);
            context.getPhaseOutputTokens().set(context.getPhaseOutputTokens().get() + execOutput);
            context.getPhaseTotalTokens().set(context.getPhaseTotalTokens().get() + execTotal);
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
        Map<String, Double> extractedTokens,
        AgentExecutionContext context
    ) {
        setCurrentResultAndLatency(context, completion, llmLatency);
        Map<String, String> updatedLLMCallAttrs = createLLMCallAttributes(completion, llmLatency, allParams, extractedTokens);
        setSpanAttributes(llmCallSpan, updatedLLMCallAttrs);
        getInstance().endSpan(llmCallSpan);
    }

    /**
     * Starts an agent task span with the given agent name and user task.
     * @param agentName The name of the agent.
     * @param userTask The user task or question.
     * @return The started Span.
     */
    public Span startAgentTaskSpan(String agentName, String userTask) {
        return startSpan(AGENT_TASK_PER_SPAN, createAgentTaskAttributes(agentName, userTask));
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
        Map<String, Double> extractedTokens,
        Span parentSpan
    ) {
        return startSpan(AGENT_LLM_CALL_SPAN, createLLMCallAttributes(completion, latency, parameters, extractedTokens), parentSpan);
    }

    /**
     * Extracts token information from ModelTensorOutput using the robust provider-specific logic.
     * @param modelTensorOutput The model output to extract tokens from.
     * @param parameters The parameters used for the LLM call (for provider detection).
     * @return A map of extracted token information.
     */
    public static Map<String, Double> extractTokensFromModelOutput(ModelTensorOutput modelTensorOutput, Map<String, String> parameters) {
        Map<String, Double> extractedTokens = new HashMap<>();
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

    private static void processTensorForTokens(ModelTensor tensor, String provider, Map<String, Double> extractedTokens) {
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

        if (provider.toLowerCase().contains("bedrock")) {
            extractBedrockTokens(usage, extractedTokens);
        } else if (provider.toLowerCase().contains("openai")) {
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
    private static void extractBedrockTokens(Map<String, Object> usage, Map<String, Double> extractedTokens) {
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

        Double inputTokensValue = null;
        Double outputTokensValue = null;
        try {
            if (inputTokens != null) {
                inputTokensValue = Double.parseDouble(inputTokens.toString());
            }
            if (outputTokens != null) {
                outputTokensValue = Double.parseDouble(outputTokens.toString());
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
            double totalTokens = inputTokensValue + outputTokensValue;
            extractedTokens.put(TOKEN_FIELD_TOTAL_TOKENS, totalTokens);
        }
    }

    /**
     * Extracts token information from OpenAI usage.
     * @param usage The usage map from OpenAI.
     * @param extractedTokens The map to store extracted tokens.
     */
    private static void extractOpenAITokens(Map<String, Object> usage, Map<String, Double> extractedTokens) {
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
                extractedTokens.put(TOKEN_FIELD_INPUT_TOKENS, Double.parseDouble(promptTokens.toString()));
            } catch (NumberFormatException e) {
                log.warn("[AGENT_TRACE] Failed to parse OpenAI prompt tokens: promptTokens={}, error={}", promptTokens, e.getMessage());
            }
        }
        if (completionTokens != null) {
            try {
                extractedTokens.put(TOKEN_FIELD_OUTPUT_TOKENS, Double.parseDouble(completionTokens.toString()));
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
                extractedTokens.put(TOKEN_FIELD_TOTAL_TOKENS, Double.parseDouble(totalTokens.toString()));
            } catch (NumberFormatException e) {
                log.warn("[AGENT_TRACE] Failed to parse OpenAI total tokens: totalTokens={}, error={}", totalTokens, e.getMessage());
            }
        }
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
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    public static void resetForTest() {
        instance = null;
    }
}
