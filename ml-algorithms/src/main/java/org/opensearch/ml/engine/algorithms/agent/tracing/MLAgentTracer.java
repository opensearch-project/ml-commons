/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

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
        attributes.put(ATTR_SERVICE_TYPE, "tracer");
        attributes.put(ATTR_NAME, agentName != null ? agentName : "");
        attributes.put(ATTR_TASK, userTask != null ? userTask : "");
        attributes.put(ATTR_OPERATION_NAME, "create_agent");
        return attributes;
    }

    /**
     * Creates attributes for a plan step span.
     * @param stepNumber The step number in the plan.
     * @return A map of attributes for the plan step span.
     */
    public static Map<String, String> createPlanAttributes(int stepNumber) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTR_SERVICE_TYPE, "tracer");
        attributes.put(ATTR_PHASE, "planner");
        attributes.put(ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        attributes.put(ATTR_OPERATION_NAME, "create_agent");
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
        attributes.put(ATTR_SERVICE_TYPE, "tracer");
        attributes.put(ATTR_PHASE, "executor");
        attributes.put(ATTR_STEP_NUMBER, String.valueOf(stepNumber));
        attributes.put(ATTR_OPERATION_NAME, "invoke_agent");
        return attributes;
    }

    /**
     * Creates attributes for an LLM call span.
     * @param completion The completion string from the LLM.
     * @param latency The latency of the LLM call.
     * @param modelTensorOutput The model tensor output.
     * @param parameters The parameters used for the LLM call.
     * @return A map of attributes for the LLM call span.
     */
    public static Map<String, String> createLLMCallAttributes(
        String completion,
        long latency,
        ModelTensorOutput modelTensorOutput,
        Map<String, String> parameters
    ) {
        Map<String, String> attributes = new HashMap<>();

        String provider = detectProviderFromParameters(parameters);
        attributes.put(ATTR_SERVICE_TYPE, "tracer");
        attributes.put(ATTR_SYSTEM, provider);
        // TODO: get actual request model
        attributes.put(ATTR_OPERATION_NAME, "chat");
        attributes.put(ATTR_TASK, parameters.get("prompt") != null ? parameters.get("prompt") : "");
        attributes.put(ATTR_RESULT, completion != null ? completion : "");
        attributes.put(ATTR_LATENCY, String.valueOf(latency));
        attributes.put(ATTR_PHASE, "planner");
        attributes.put(ATTR_SYSTEM_MESSAGE, parameters.get("system_prompt") != null ? parameters.get("system_prompt") : "");
        attributes.put(ATTR_TOOL_DESCRIPTION, parameters.get("tools_prompt") != null ? parameters.get("tools_prompt") : "");

        if (modelTensorOutput == null || modelTensorOutput.getMlModelOutputs() == null || modelTensorOutput.getMlModelOutputs().isEmpty()) {
            log.info("[AGENT_TRACE] ModelTensorOutput is null or empty");
            return attributes;
        }
        for (var output : modelTensorOutput.getMlModelOutputs()) {
            if (output.getMlModelTensors() == null)
                continue;
            for (var tensor : output.getMlModelTensors()) {
                if (tensor.getDataAsMap() == null)
                    continue;
                Map<String, ?> dataAsMap = tensor.getDataAsMap();
                if (!dataAsMap.containsKey("usage"))
                    continue;
                Object usageObj = dataAsMap.get("usage");
                if (!(usageObj instanceof Map))
                    continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> usage = (Map<String, Object>) usageObj;

                if (provider != null && provider.toLowerCase().contains("bedrock")) {
                    Object inputTokens = null;
                    Object outputTokens = null;
                    for (String key : usage.keySet()) {
                        if (key.equalsIgnoreCase("input_tokens") || key.equalsIgnoreCase("inputTokens")) {
                            inputTokens = usage.get(key);
                        }
                        if (key.equalsIgnoreCase("output_tokens") || key.equalsIgnoreCase("outputTokens")) {
                            outputTokens = usage.get(key);
                        }
                    }
                    if (inputTokens != null) {
                        attributes.put(ATTR_USAGE_INPUT_TOKENS, inputTokens.toString());
                    }
                    if (outputTokens != null) {
                        attributes.put(ATTR_USAGE_OUTPUT_TOKENS, outputTokens.toString());
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
                    } catch (NumberFormatException e) {}
                    if (inputTokensValue != null && outputTokensValue != null) {
                        double totalTokens = inputTokensValue + outputTokensValue;
                        attributes.put(ATTR_USAGE_TOTAL_TOKENS, String.valueOf((int) totalTokens));
                    }
                } else if (provider != null && provider.toLowerCase().contains("openai")) {
                    Object promptTokens = null;
                    Object completionTokens = null;
                    Object totalTokens = null;
                    for (String key : usage.keySet()) {
                        if (key.equalsIgnoreCase("prompt_tokens")) {
                            promptTokens = usage.get(key);
                        }
                        if (key.equalsIgnoreCase("completion_tokens")) {
                            completionTokens = usage.get(key);
                        }
                        if (key.equalsIgnoreCase("total_tokens")) {
                            totalTokens = usage.get(key);
                        }
                    }
                    if (promptTokens != null) {
                        attributes.put(ATTR_USAGE_INPUT_TOKENS, promptTokens.toString());
                    }
                    if (completionTokens != null) {
                        attributes.put(ATTR_USAGE_OUTPUT_TOKENS, completionTokens.toString());
                    }
                    if (totalTokens != null) {
                        try {
                            Double.parseDouble(totalTokens.toString());
                            attributes.put(ATTR_USAGE_TOTAL_TOKENS, totalTokens.toString());
                        } catch (NumberFormatException e) {}
                    }
                } else {
                    // TODO: find general method for all providers
                }
            }
        }

        return attributes;
    }

    /**
     * Detects the provider from the parameters map.
     * @param parameters The parameters map.
     * @return The provider string (e.g., "openai", "aws.bedrock", etc.), or "unknown" if not detected.
     */
    public static String detectProviderFromParameters(Map<String, String> parameters) {
        String llmInterface = parameters.get("_llm_interface");
        if (llmInterface != null) {
            String lower = llmInterface.toLowerCase();
            if (lower.contains("bedrock"))
                return "aws.bedrock";
            if (lower.contains("openai"))
                return "openai";
            if (lower.contains("claude") || lower.contains("anthropic"))
                return "anthropic";
            if (lower.contains("gemini") || lower.contains("google"))
                return "gcp.gemini";
            if (lower.contains("llama") || lower.contains("meta"))
                return "meta";
            if (lower.contains("cohere"))
                return "cohere";
            if (lower.contains("deepseek"))
                return "deepseek";
            if (lower.contains("groq"))
                return "groq";
            if (lower.contains("mistral"))
                return "mistral_ai";
            if (lower.contains("perplexity"))
                return "perplexity";
            if (lower.contains("xai"))
                return "xai";
            if (lower.contains("azure") || lower.contains("az.ai"))
                return "az.ai.inference";
            if (lower.contains("ibm") || lower.contains("watson"))
                return "ibm.watsonx.ai";
        }
        return "unknown";
    }

    /**
     * Container for tool call extraction results, including input, output, usage, and metrics.
     */
    public static class ToolCallExtractionResult {
        public String input;
        public String output;
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
            result.output = toolOutput != null ? toolOutput.toString() : null;
            return result;
        }
        ModelTensorOutput mto = (ModelTensorOutput) toolOutput;
        if (mto.getMlModelOutputs() == null || mto.getMlModelOutputs().isEmpty())
            return result;
        var tensors = mto.getMlModelOutputs().get(0).getMlModelTensors();
        if (tensors == null || tensors.isEmpty())
            return result;
        var tensor = tensors.get(0);
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
        if (map == null) {
            if (result.output == null) {
                result.output = tensor.toString();
                log.warn("[AGENT_TRACE] tensor.getDataAsMap() is null; using tensor.toString() as output");
            }
            return result;
        }
        if (map.containsKey("response")) {
            Object resp = map.get("response");
            result.output = (resp instanceof String) ? (String) resp : StringUtils.toJson(resp);
        } else if (map.containsKey("output")) {
            Object out = map.get("output");
            result.output = (out instanceof String) ? (String) out : StringUtils.toJson(out);
        } else if (result.output == null && !map.isEmpty()) {
            Object firstValue = map.values().iterator().next();
            result.output = (firstValue instanceof String) ? (String) firstValue : StringUtils.toJson(firstValue);
        }
        if (map.containsKey("usage")) {
            try {
                result.usage = (Map<String, Object>) map.get("usage");
            } catch (ClassCastException e) {
                log.warn("[AGENT_TRACE] 'usage' field is not a Map: {}", e.getMessage());
            }
        }
        if (map.containsKey("metrics")) {
            try {
                result.metrics = (Map<String, Object>) map.get("metrics");
            } catch (ClassCastException e) {
                log.warn("[AGENT_TRACE] 'metrics' field is not a Map: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Updates the given span with result attributes such as result, input tokens, output tokens, total tokens, and latency.
     * @param span The span to update.
     * @param result The result string.
     * @param inputTokens The number of input tokens.
     * @param outputTokens The number of output tokens.
     * @param totalTokens The total number of tokens.
     * @param latency The latency value.
     */
    public static void updateSpanWithResultAttributes(
        Span span,
        String result,
        Double inputTokens,
        Double outputTokens,
        Double totalTokens,
        Double latency
    ) {
        if (span == null)
            return;
        if (result != null) {
            span.addAttribute(ATTR_RESULT, result);
        }
        if (inputTokens != null) {
            span.addAttribute(ATTR_USAGE_INPUT_TOKENS, String.valueOf(inputTokens.intValue()));
        }
        if (outputTokens != null) {
            span.addAttribute(ATTR_USAGE_OUTPUT_TOKENS, String.valueOf(outputTokens.intValue()));
        }
        if (totalTokens != null) {
            span.addAttribute(ATTR_USAGE_TOTAL_TOKENS, String.valueOf(totalTokens.intValue()));
        }
        if (latency != null) {
            span.addAttribute(ATTR_LATENCY, String.valueOf(latency.intValue()));
        }
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
        String spanName;
        if (stepsExecuted == 0) {
            spanName = AGENT_PLAN_SPAN;
        } else {
            spanName = String.format(AGENT_REFLECT_STEP_SPAN + "_%d", stepsExecuted);
        }
        return startSpan(spanName, createPlanAttributes(stepsExecuted), parentSpan);
    }

    /**
     * Starts an execute step span with the given step number and parent span.
     * @param stepNumber The step number in the execution.
     * @param parentSpan The parent span.
     * @return The started Span.
     */
    public Span startExecuteStepSpan(int stepNumber, Span parentSpan) {
        return startSpan(AGENT_EXECUTE_STEP_SPAN + "_" + stepNumber, createExecuteStepAttributes(stepNumber), parentSpan);
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
        ModelTensorOutput modelTensorOutput,
        Map<String, String> parameters,
        Span parentSpan
    ) {
        return startSpan(AGENT_LLM_CALL_SPAN, createLLMCallAttributes(completion, latency, modelTensorOutput, parameters), parentSpan);
    }

    /**
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    public static void resetForTest() {
        instance = null;
    }
}
