/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static org.opensearch.ml.common.FunctionName.REMOTE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ActivationRule;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Context manager that implements summarization approach for tool interactions.
 * Summarizes older interactions while preserving recent ones to manage context window.
 */
@Log4j2
public class SummarizationManager implements ContextManager {

    public static final String TYPE = "SummarizationManager";

    // Configuration keys
    private static final String SUMMARY_RATIO_KEY = "summary_ratio";
    private static final String PRESERVE_RECENT_MESSAGES_KEY = "preserve_recent_messages";
    private static final String SUMMARIZATION_MODEL_ID_KEY = "summarization_model_id";
    private static final String SUMMARIZATION_SYSTEM_PROMPT_KEY = "summarization_system_prompt";

    // Default values
    private static final double DEFAULT_SUMMARY_RATIO = 0.3;
    private static final int DEFAULT_PRESERVE_RECENT_MESSAGES = 10;
    private static final String DEFAULT_SUMMARIZATION_PROMPT =
        "You are a tool interactions summarization agent. Summarize the provided tool interactions concisely while preserving key information and context.";

    protected double summaryRatio;
    protected int preserveRecentMessages;
    protected String summarizationModelId;
    protected String summarizationSystemPrompt;
    protected List<ActivationRule> activationRules;
    private Client client;

    public SummarizationManager(Client client) {
        this.client = client;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void initialize(Map<String, Object> config) {
        this.summaryRatio = parseDoubleConfig(config, SUMMARY_RATIO_KEY, DEFAULT_SUMMARY_RATIO);
        this.preserveRecentMessages = parseIntegerConfig(config, PRESERVE_RECENT_MESSAGES_KEY, DEFAULT_PRESERVE_RECENT_MESSAGES);
        this.summarizationModelId = (String) config.get(SUMMARIZATION_MODEL_ID_KEY);
        this.summarizationSystemPrompt = (String) config.getOrDefault(SUMMARIZATION_SYSTEM_PROMPT_KEY, DEFAULT_SUMMARIZATION_PROMPT);

        // Validate summary ratio
        if (summaryRatio < 0.1 || summaryRatio > 0.8) {
            log.warn("Invalid summary_ratio value: {}, using default {}", summaryRatio, DEFAULT_SUMMARY_RATIO);
            this.summaryRatio = DEFAULT_SUMMARY_RATIO;
        }

        // Initialize activation rules from config
        @SuppressWarnings("unchecked")
        Map<String, Object> activationConfig = (Map<String, Object>) config.get("activation");
        this.activationRules = ActivationRuleFactory.createRules(activationConfig);

        log.info("Initialized SummarizationManager: summaryRatio={}, preserveRecentMessages={}", summaryRatio, preserveRecentMessages);
    }

    @Override
    public boolean shouldActivate(ContextManagerContext context) {
        if (activationRules == null || activationRules.isEmpty()) {
            return true;
        }

        for (ActivationRule rule : activationRules) {
            if (!rule.evaluate(context)) {
                log.debug("Activation rule not satisfied: {}", rule.getDescription());
                return false;
            }
        }

        log.debug("All activation rules satisfied, manager will execute");
        return true;
    }

    @Override
    public void execute(ContextManagerContext context) {
        List<Map<String, Object>> toolInteractions = context.getToolInteractions();

        if (toolInteractions == null || toolInteractions.isEmpty()) {
            log.debug("No tool interactions to process");
            return;
        }

        // Extract interactions from tool interactions
        List<String> interactions = new ArrayList<>();
        for (Map<String, Object> toolInteraction : toolInteractions) {
            Object output = toolInteraction.get("output");
            if (output instanceof String) {
                interactions.add((String) output);
            }
        }

        if (interactions.isEmpty()) {
            log.debug("No string interactions found in tool interactions");
            return;
        }

        int totalMessages = interactions.size();

        // Calculate how many messages to summarize
        int messagesToSummarizeCount = Math.max(1, (int) (totalMessages * summaryRatio));

        // Ensure we don't summarize recent messages
        messagesToSummarizeCount = Math.min(messagesToSummarizeCount, totalMessages - preserveRecentMessages);

        if (messagesToSummarizeCount <= 0) {
            log.debug("Cannot summarize: insufficient messages for summarization");
            return;
        }

        // Extract messages to summarize and remaining messages
        List<String> messagesToSummarize = new ArrayList<>(interactions.subList(0, messagesToSummarizeCount));
        List<String> remainingMessages = new ArrayList<>(interactions.subList(messagesToSummarizeCount, totalMessages));

        // Get model ID
        String modelId = summarizationModelId;
        if (modelId == null) {
            Map<String, Object> parameters = context.getParameters();
            if (parameters != null) {
                modelId = (String) parameters.get("_llm_model_id");
            }
        }

        if (modelId == null) {
            log.error("No model ID available for summarization");
            return;
        }

        // Prepare summarization parameters
        Map<String, String> summarizationParameters = new HashMap<>();
        summarizationParameters.put("prompt", StringUtils.toJson(String.join("\n", messagesToSummarize)));
        summarizationParameters.put("system_prompt", summarizationSystemPrompt);

        executeSummarization(context, modelId, summarizationParameters, messagesToSummarizeCount, remainingMessages, toolInteractions);
    }

    protected void executeSummarization(
        ContextManagerContext context,
        String modelId,
        Map<String, String> summarizationParameters,
        int messagesToSummarizeCount,
        List<String> remainingMessages,
        List<Map<String, Object>> originalToolInteractions
    ) {
        try {
            // Create ML input dataset for remote inference
            MLInputDataset inputDataset = RemoteInferenceInputDataSet.builder().parameters(summarizationParameters).build();

            // Create ML input
            MLInput mlInput = MLInput.builder().algorithm(REMOTE).inputDataset(inputDataset).build();

            // Create prediction request
            MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().modelId(modelId).mlInput(mlInput).build();

            // Execute prediction
            ActionListener<MLTaskResponse> listener = ActionListener.wrap(response -> {
                try {
                    String summary = extractSummaryFromResponse(response);
                    processSummarizationResult(context, summary, messagesToSummarizeCount, remainingMessages, originalToolInteractions);
                } catch (Exception e) {
                    log.error("Failed to process summarization response", e);
                    // Fallback to default behavior
                    processSummarizationResult(
                        context,
                        "Summarized " + messagesToSummarizeCount + " previous tool interactions",
                        messagesToSummarizeCount,
                        remainingMessages,
                        originalToolInteractions
                    );
                }
            }, e -> {
                log.error("Summarization prediction failed", e);
                // Fallback to default behavior
                processSummarizationResult(
                    context,
                    "Summarized " + messagesToSummarizeCount + " previous tool interactions",
                    messagesToSummarizeCount,
                    remainingMessages,
                    originalToolInteractions
                );
            });

            client.execute(MLPredictionTaskAction.INSTANCE, request, listener);

        } catch (Exception e) {
            log.error("Failed to execute summarization", e);
            // Fallback to default behavior
            processSummarizationResult(
                context,
                "Summarized " + messagesToSummarizeCount + " previous tool interactions",
                messagesToSummarizeCount,
                remainingMessages,
                originalToolInteractions
            );
        }
    }

    protected void processSummarizationResult(
        ContextManagerContext context,
        String summary,
        int messagesToSummarizeCount,
        List<String> remainingMessages,
        List<Map<String, Object>> originalToolInteractions
    ) {
        try {
            // Create summarized interaction
            String summarizedInteraction = "{\"role\":\"tool\",\"content\":\"Summarized previous tool interactions: " + summary + "\"}";

            // Update interactions: summary + remaining messages
            List<String> updatedInteractions = new ArrayList<>();
            updatedInteractions.add(summarizedInteraction);
            updatedInteractions.addAll(remainingMessages);

            // Update toolInteractions in context
            List<Map<String, Object>> updatedToolInteractions = new ArrayList<>();

            // Add summary as first interaction
            Map<String, Object> summaryInteraction = new HashMap<>();
            summaryInteraction.put("output", summarizedInteraction);
            updatedToolInteractions.add(summaryInteraction);

            // Add remaining tool interactions
            for (int i = messagesToSummarizeCount; i < originalToolInteractions.size(); i++) {
                updatedToolInteractions.add(originalToolInteractions.get(i));
            }

            context.setToolInteractions(updatedToolInteractions);

            // Update parameters
            Map<String, Object> parameters = context.getParameters();
            if (parameters == null) {
                parameters = new HashMap<>();
                context.setParameters(parameters);
            }
            parameters.put("_interactions", ", " + String.join(", ", updatedInteractions));

            log
                .info(
                    "Summarization completed: {} messages summarized, {} messages preserved",
                    messagesToSummarizeCount,
                    remainingMessages.size()
                );

        } catch (Exception e) {
            log.error("Failed to process summarization result", e);
        }
    }

    private String extractSummaryFromResponse(MLTaskResponse response) {
        try {
            MLOutput output = response.getOutput();
            if (output instanceof ModelTensorOutput) {
                ModelTensorOutput tensorOutput = (ModelTensorOutput) output;
                List<ModelTensors> mlModelOutputs = tensorOutput.getMlModelOutputs();

                if (mlModelOutputs != null && !mlModelOutputs.isEmpty()) {
                    List<ModelTensor> tensors = mlModelOutputs.get(0).getMlModelTensors();
                    if (tensors != null && !tensors.isEmpty()) {
                        Map<String, ?> dataAsMap = tensors.get(0).getDataAsMap();
                        // TODO need to parse LLM response output, maybe reused how filtered output from chatAgentRunner
                        return StringUtils.toJson(dataAsMap);
                        // if (dataAsMap.containsKey("response")) {
                        // return dataAsMap.get("response").toString();
                        // }
                        // if (dataAsMap.containsKey("result")) {
                        // return dataAsMap.get("result").toString();
                        // }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract summary from response", e);
        }

        return "Summary generation failed";
    }

    private double parseDoubleConfig(Map<String, Object> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            } else {
                log.warn("Invalid type for config key '{}': {}, using default {}", key, value.getClass().getSimpleName(), defaultValue);
                return defaultValue;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid double value for config key '{}': {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private int parseIntegerConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else {
                log.warn("Invalid type for config key '{}': {}, using default {}", key, value.getClass().getSimpleName(), defaultValue);
                return defaultValue;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for config key '{}': {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
