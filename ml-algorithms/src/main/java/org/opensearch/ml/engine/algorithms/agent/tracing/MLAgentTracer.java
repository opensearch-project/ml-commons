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
        attributes.put("service.type", "tracer");
        attributes.put("gen_ai.agent.name", agentName != null ? agentName : "");
        attributes.put("gen_ai.agent.task", userTask != null ? userTask : "");
        attributes.put("gen_ai.operation.name", "create_agent");
        return attributes;
    }

    /**
     * Creates attributes for a plan step span.
     * @param stepNumber The step number in the plan.
     * @return A map of attributes for the plan step span.
     */
    public static Map<String, String> createPlanAttributes(int stepNumber) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("service.type", "tracer");
        attributes.put("gen_ai.agent.phase", "planner");
        attributes.put("gen_ai.agent.step.number", String.valueOf(stepNumber));
        attributes.put("gen_ai.operation.name", "create_agent");
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
        attributes.put("service.type", "tracer");
        attributes.put("gen_ai.agent.phase", "executor");
        attributes.put("gen_ai.agent.step.number", String.valueOf(stepNumber));
        attributes.put("gen_ai.operation.name", "invoke_agent");
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
        attributes.put("service.type", "tracer");
        attributes.put("gen_ai.system", provider);
        // TODO: get actual request model
        attributes.put("gen_ai.operation.name", "chat");
        attributes.put("gen_ai.agent.task", parameters.get("prompt") != null ? parameters.get("prompt") : "");
        attributes.put("gen_ai.agent.result", completion != null ? completion : "");
        attributes.put("gen_ai.agent.latency", String.valueOf(latency));
        attributes.put("gen_ai.agent.phase", "planner");
        attributes.put("gen_ai.system.message", parameters.get("system_prompt") != null ? parameters.get("system_prompt") : "");
        attributes.put("gen_ai.tool.description", parameters.get("tools_prompt") != null ? parameters.get("tools_prompt") : "");

        if (modelTensorOutput != null
            && modelTensorOutput.getMlModelOutputs() != null
            && !modelTensorOutput.getMlModelOutputs().isEmpty()) {
            for (int i = 0; i < modelTensorOutput.getMlModelOutputs().size(); i++) {
                var output = modelTensorOutput.getMlModelOutputs().get(i);
                if (output.getMlModelTensors() != null) {
                    for (int j = 0; j < output.getMlModelTensors().size(); j++) {
                        var tensor = output.getMlModelTensors().get(j);
                        if (tensor.getDataAsMap() != null) {
                            Map<String, ?> dataAsMap = tensor.getDataAsMap();
                            if (dataAsMap.containsKey("usage")) {
                                Object usageObj = dataAsMap.get("usage");
                                if (usageObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> usage = (Map<String, Object>) usageObj;

                                    if ("aws.bedrock".equalsIgnoreCase(provider)) {
                                        // Bedrock/Claude format: input_tokens, output_tokens (or inputTokens, outputTokens)
                                        Object inputTokens = null;
                                        if (usage.containsKey("input_tokens")) {
                                            inputTokens = usage.get("input_tokens");
                                        } else if (usage.containsKey("inputTokens")) {
                                            inputTokens = usage.get("inputTokens");
                                        }
                                        if (inputTokens != null) {
                                            attributes.put("gen_ai.usage.input_tokens", inputTokens.toString());
                                        }

                                        Object outputTokens = null;
                                        if (usage.containsKey("output_tokens")) {
                                            outputTokens = usage.get("output_tokens");
                                        } else if (usage.containsKey("outputTokens")) {
                                            outputTokens = usage.get("outputTokens");
                                        }
                                        if (outputTokens != null) {
                                            attributes.put("gen_ai.usage.output_tokens", outputTokens.toString());
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
                                            attributes.put("gen_ai.usage.total_tokens", String.valueOf((int) totalTokens));
                                        }
                                    } else if ("openai".equalsIgnoreCase(provider)) {
                                        // OpenAI format: prompt_tokens, completion_tokens, total_tokens
                                        Object promptTokens = null;
                                        if (usage.containsKey("prompt_tokens")) {
                                            promptTokens = usage.get("prompt_tokens");
                                            if (promptTokens != null) {
                                                attributes.put("gen_ai.usage.input_tokens", promptTokens.toString());
                                            }
                                        }

                                        Object completionTokens = null;
                                        if (usage.containsKey("completion_tokens")) {
                                            completionTokens = usage.get("completion_tokens");
                                            if (completionTokens != null) {
                                                attributes.put("gen_ai.usage.output_tokens", completionTokens.toString());
                                            }
                                        }

                                        Object totalTokens = null;
                                        if (usage.containsKey("total_tokens")) {
                                            totalTokens = usage.get("total_tokens");
                                            if (totalTokens != null) {
                                                try {
                                                    Double.parseDouble(totalTokens.toString());
                                                    attributes.put("gen_ai.usage.total_tokens", totalTokens.toString());
                                                } catch (NumberFormatException e) {}
                                            }
                                        }
                                    } else {
                                        // TODO: find general method for all providers
                                    }
                                }
                            } else {
                                log.info("[AGENT_TRACE] No usage information found in dataAsMap. Available keys: {}", dataAsMap.keySet());

                                for (Map.Entry<String, ?> entry : dataAsMap.entrySet()) {
                                    if (entry.getValue() instanceof Map) {
                                        log.info("[AGENT_TRACE] Found nested map in key '{}': {}", entry.getKey(), entry.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            log.info("[AGENT_TRACE] ModelTensorOutput is null or empty");
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

        try {
            // ModelTensorOutput
            if (toolOutput instanceof ModelTensorOutput) {
                ModelTensorOutput mto = (ModelTensorOutput) toolOutput;
                if (mto.getMlModelOutputs() != null && !mto.getMlModelOutputs().isEmpty()) {
                    var tensors = mto.getMlModelOutputs().get(0).getMlModelTensors();
                    if (tensors != null && !tensors.isEmpty()) {
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
                        if (map != null) {
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
                        } else if (result.output == null) {
                            result.output = tensor.toString();
                            log.warn("[AGENT_TRACE] tensor.getDataAsMap() is null; using tensor.toString() as output");
                        }
                    }
                }
                return result;
            }
            // Fallback: toString
            result.output = toolOutput != null ? toolOutput.toString() : null;
        } catch (Exception e) {
            result.output = toolOutput != null ? toolOutput.toString() : null;
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
            span.addAttribute("gen_ai.agent.result", result);
        }
        if (inputTokens != null) {
            span.addAttribute("gen_ai.usage.input_tokens", String.valueOf(inputTokens.intValue()));
        }
        if (outputTokens != null) {
            span.addAttribute("gen_ai.usage.output_tokens", String.valueOf(outputTokens.intValue()));
        }
        if (totalTokens != null) {
            span.addAttribute("gen_ai.usage.total_tokens", String.valueOf(totalTokens.intValue()));
        }
        if (latency != null) {
            span.addAttribute("gen_ai.agent.latency", String.valueOf(latency.intValue()));
        }
    }

    /**
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    public static void resetForTest() {
        instance = null;
    }
}
