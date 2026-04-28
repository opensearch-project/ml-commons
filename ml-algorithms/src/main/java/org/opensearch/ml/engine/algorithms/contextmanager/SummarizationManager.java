/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static java.lang.Math.min;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.INTERACTIONS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ActivationRule;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

/**
 * Context manager that implements summarization approach for tool interactions.
 * Summarizes older interactions while preserving recent ones to manage context
 * window.
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
        "You are a interactions summarization agent. Summarize the provided interactions concisely while preserving key information and context.";

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

        log
            .debug(
                "Initialized SummarizationManager: summaryRatio={}, preserveRecentMessages={}, activationRules={}",
                summaryRatio,
                preserveRecentMessages,
                activationRules != null ? activationRules.size() : 0
            );
    }

    @Override
    public boolean shouldActivate(ContextManagerContext context) {
        int tokenCount = context.getEstimatedTokenCount();

        if (activationRules == null || activationRules.isEmpty()) {
            log.debug("No activation rules configured, SummarizationManager will always activate");
            return true;
        }

        for (ActivationRule rule : activationRules) {
            if (!rule.evaluate(context)) {
                log.debug("Activation rule not satisfied: {}", rule.getDescription());
                return false;
            }
        }

        log.debug("All activation rules satisfied, tokenCount={}", tokenCount);
        return true;
    }

    @Override
    public void execute(ContextManagerContext context) {
        summarizeToolInteractions(context);
        summarizeStructuredChatHistory(context);
    }

    private void summarizeToolInteractions(ContextManagerContext context) {
        List<String> interactions = context.getToolInteractions();

        if (interactions == null || interactions.isEmpty()) {
            return;
        }

        int totalMessages = interactions.size();

        // Calculate how many messages to summarize
        int messagesToSummarizeCount = Math.max(1, (int) (totalMessages * summaryRatio));

        // Ensure we don't summarize recent messages
        messagesToSummarizeCount = min(messagesToSummarizeCount, totalMessages - preserveRecentMessages);

        if (messagesToSummarizeCount <= 0) {
            return;
        }

        // Find a safe cut point that doesn't break assistant-tool pairs
        int safeCutPoint = ContextManagerUtils.findSafePoint(interactions, messagesToSummarizeCount, false);

        if (safeCutPoint <= 0) {
            return;
        }

        // Extract messages to summarize and remaining messages
        List<String> messagesToSummarize = new ArrayList<>(interactions.subList(0, safeCutPoint));
        List<String> remainingMessages = new ArrayList<>(interactions.subList(safeCutPoint, totalMessages));

        // Get model ID
        String modelId = resolveModelId(context);

        if (modelId == null) {
            log.error("No model ID available for summarization");
            return;
        }

        // Prepare summarization parameters
        Map<String, String> summarizationParameters = new HashMap<>();
        summarizationParameters.put("prompt", "Help summarize the following" + StringUtils.toJson(String.join(",", messagesToSummarize)));
        summarizationParameters.put("system_prompt", summarizationSystemPrompt);

        executeSummarization(context, modelId, summarizationParameters, safeCutPoint, remainingMessages, interactions);
    }

    private void summarizeStructuredChatHistory(ContextManagerContext context) {
        List<Message> structuredHistory = context.getStructuredChatHistory();

        if (structuredHistory == null || structuredHistory.isEmpty()) {
            log.debug("No structured chat history to summarize");
            return;
        }

        int totalMessages = structuredHistory.size();
        log
            .info(
                "Structured chat history: {} messages, preserveRecentMessages={}, summaryRatio={}",
                totalMessages,
                preserveRecentMessages,
                summaryRatio
            );

        if (totalMessages < 2) {
            log.debug("Need at least 2 structured messages to summarize, have {}", totalMessages);
            return;
        }

        // Calculate how many messages to summarize
        int messagesToSummarizeCount = Math.max(1, (int) (totalMessages * summaryRatio));

        // For structured messages, each message can be very long (unlike tool interactions).
        // Cap preserveRecentMessages at totalMessages - 1 so we always summarize at least
        // the oldest message when activation rules are satisfied.
        int effectivePreserve = Math.max(1, min(preserveRecentMessages, totalMessages - 1));
        messagesToSummarizeCount = min(messagesToSummarizeCount, totalMessages - effectivePreserve);

        if (messagesToSummarizeCount <= 0) {
            log.info("Not enough structured messages to summarize: total={}, effectivePreserve={}", totalMessages, effectivePreserve);
            return;
        }

        // Find a safe cut point that doesn't break assistant-toolCall / tool-result pairs
        int safeCutPoint = ContextManagerUtils.findSafeCutPointForStructuredMessages(structuredHistory, messagesToSummarizeCount);

        if (safeCutPoint <= 0 || safeCutPoint >= totalMessages) {
            log.info("No safe cut point found for structured messages: target={}, safe={}", messagesToSummarizeCount, safeCutPoint);
            return;
        }

        log
            .info(
                "Will summarize {} of {} structured messages (safe cut point: {}), preserving {} recent messages",
                safeCutPoint,
                totalMessages,
                safeCutPoint,
                totalMessages - safeCutPoint
            );

        // Extract text from older messages to summarize
        List<Message> messagesToSummarize = structuredHistory.subList(0, safeCutPoint);
        List<Message> remainingMessages = new ArrayList<>(structuredHistory.subList(safeCutPoint, totalMessages));

        StringBuilder textToSummarize = new StringBuilder();
        for (Message message : messagesToSummarize) {
            if (message.getContent() != null) {
                for (ContentBlock block : message.getContent()) {
                    if (block.getText() != null) {
                        textToSummarize.append(block.getText()).append("\n");
                    }
                }
            }
        }

        if (textToSummarize.length() == 0) {
            return;
        }

        // Get model ID
        String modelId = resolveModelId(context);

        if (modelId == null) {
            log.error("No model ID available for structured chat history summarization");
            return;
        }

        // Prepare summarization parameters.
        // Format as structured body for connector templates that use ${parameters.body}
        String userPromptText = "Help summarize the following:\n" + textToSummarize.toString();
        String escapedPrompt = processTextDoc(userPromptText);
        String body = "{\"role\":\"user\",\"content\":[{\"text\":\"" + escapedPrompt + "\"}]}";

        Map<String, String> summarizationParameters = new HashMap<>();
        summarizationParameters.put("body", body);
        summarizationParameters.put("system_prompt", summarizationSystemPrompt);
        // Also set prompt for connectors that use ${parameters.prompt} instead of body
        summarizationParameters.put("prompt", userPromptText);

        executeSummarizationForStructuredHistory(context, modelId, summarizationParameters, remainingMessages);
    }

    private String resolveModelId(ContextManagerContext context) {
        String modelId = summarizationModelId;
        if (modelId == null) {
            Map<String, String> parameters = context.getParameters();
            if (parameters != null) {
                modelId = (String) parameters.get("_llm_model_id");
            }
        }
        return modelId;
    }

    private String resolveTenantId(ContextManagerContext context) {
        Map<String, String> parameters = context.getParameters();
        if (parameters != null) {
            return parameters.get(TENANT_ID_FIELD);
        }
        return null;
    }

    protected void executeSummarization(
        ContextManagerContext context,
        String modelId,
        Map<String, String> summarizationParameters,
        int messagesToSummarizeCount,
        List<String> remainingMessages,
        List<String> originalInteractions
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean(false);

        try {
            // Create ML input dataset for remote inference
            MLInputDataset inputDataset = RemoteInferenceInputDataSet.builder().parameters(summarizationParameters).build();

            // Create ML input
            MLInput mlInput = MLInput.builder().algorithm(REMOTE).inputDataset(inputDataset).build();

            // Propagate tenant_id from context so the prediction request passes multi-tenancy validation
            String tenantId = resolveTenantId(context);

            // Create prediction request
            MLPredictionTaskRequest request = MLPredictionTaskRequest
                .builder()
                .modelId(modelId)
                .mlInput(mlInput)
                .tenantId(tenantId)
                .build();

            // Execute prediction
            ActionListener<MLTaskResponse> listener = ActionListener.wrap(response -> {
                try {
                    if (timedOut.get()) {
                        return;
                    }
                    String summary = extractSummaryFromResponse(response, context);
                    if (summary != null) {
                        processSummarizationResult(context, summary, messagesToSummarizeCount, remainingMessages, originalInteractions);
                    } else {
                        // Summary extraction failed, keep original interactions
                        log.warn("Summary extraction failed, keeping original interactions");
                    }
                } catch (Exception e) {
                    // Fallback: skip summarization, keep original interactions
                    log.warn("Summarization failed, keeping original interactions: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, e -> {
                if (!timedOut.get()) {
                    // Fallback: skip summarization, keep original interactions
                    log.warn("Summarization request failed, keeping original interactions: {}", e.getMessage());
                }
                latch.countDown();
            });

            // Dispatch client call on generic thread pool to avoid transport thread
            // deadlock (blocking current thread with latch.await while the prediction
            // callback needs a transport thread to fire). OpenSearch's thread pool
            // automatically preserves the thread context (including security credentials).
            client.threadPool().generic().execute(() -> {
                try {
                    client.execute(MLPredictionTaskAction.INSTANCE, request, listener);
                } catch (Exception e) {
                    log.warn("Failed to dispatch summarization request: {}", e.getMessage());
                    latch.countDown();
                }
            });

            // Wait for summarization to complete (30 second timeout)
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                timedOut.set(true);
                log.warn("Summarization timed out after 30s; skipping late results");
            }

        } catch (Exception e) {
            // Fallback: skip summarization, keep original interactions
            log.warn("Summarization setup failed, keeping original interactions: {}", e.getMessage());
        }
    }

    protected void executeSummarizationForStructuredHistory(
        ContextManagerContext context,
        String modelId,
        Map<String, String> summarizationParameters,
        List<Message> remainingMessages
    ) {
        log.info("Starting structured history summarization with model: {}", modelId);
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean(false);

        try {
            // Create ML input dataset for remote inference
            MLInputDataset inputDataset = RemoteInferenceInputDataSet.builder().parameters(summarizationParameters).build();

            // Create ML input
            MLInput mlInput = MLInput.builder().algorithm(REMOTE).inputDataset(inputDataset).build();

            // Propagate tenant_id from context so the prediction request passes multi-tenancy validation
            String tenantId = resolveTenantId(context);

            // Create prediction request
            MLPredictionTaskRequest request = MLPredictionTaskRequest
                .builder()
                .modelId(modelId)
                .mlInput(mlInput)
                .tenantId(tenantId)
                .build();

            // Execute prediction
            ActionListener<MLTaskResponse> listener = ActionListener.wrap(response -> {
                try {
                    if (timedOut.get()) {
                        log.warn("Structured history summarization response arrived after timeout, discarding");
                        return;
                    }
                    String summary = extractSummaryFromResponse(response, context);
                    if (summary != null) {
                        log.info("Structured history summarization LLM call succeeded, summary length: {}", summary.length());
                        processStructuredSummarizationResult(context, summary, remainingMessages);
                    } else {
                        log.warn("Summary extraction failed for structured history, keeping original messages");
                    }
                } catch (Exception e) {
                    log.warn("Structured history summarization failed, keeping original messages: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, e -> {
                if (!timedOut.get()) {
                    log.warn("Structured history summarization request failed, keeping original messages: {}", e.getMessage());
                }
                latch.countDown();
            });

            // Dispatch client call on generic thread pool to avoid transport thread
            // deadlock (blocking current thread with latch.await while the prediction
            // callback needs a transport thread to fire). OpenSearch's thread pool
            // automatically preserves the thread context (including security credentials).
            client.threadPool().generic().execute(() -> {
                try {
                    client.execute(MLPredictionTaskAction.INSTANCE, request, listener);
                } catch (Exception e) {
                    log.warn("Failed to dispatch structured history summarization request: {}", e.getMessage());
                    latch.countDown();
                }
            });
            log.info("Structured history summarization request dispatched, waiting for response...");

            // Wait for summarization to complete (30 second timeout)
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                timedOut.set(true);
                log.warn("Structured history summarization timed out after 30s; skipping late results");
            } else {
                log.info("Structured history summarization latch completed");
            }

        } catch (Exception e) {
            log.warn("Structured history summarization setup failed, keeping original messages: {}", e.getMessage());
        }
    }

    protected void processStructuredSummarizationResult(ContextManagerContext context, String summary, List<Message> remainingMessages) {
        try {
            // Create summary content block
            ContentBlock summaryBlock = new ContentBlock();
            summaryBlock.setType(ContentType.TEXT);
            summaryBlock.setText("Summarized previous interactions: " + summary);

            // Create summary message with assistant role
            Message summaryMessage = new Message("assistant", List.of(summaryBlock));

            // Combine summary message with remaining messages
            List<Message> updatedHistory = new ArrayList<>();
            updatedHistory.add(summaryMessage);
            updatedHistory.addAll(remainingMessages);

            context.setStructuredChatHistory(updatedHistory);

            log.info("Structured history summarization completed: {} messages preserved", remainingMessages.size());
        } catch (Exception e) {
            log.error("Failed to process structured history summarization result", e);
        }
    }

    protected void processSummarizationResult(
        ContextManagerContext context,
        String summary,
        int messagesToSummarizeCount,
        List<String> remainingMessages,
        List<String> originalInteractions
    ) {
        try {
            // Create summarized interaction
            String summarizedInteraction =
                "{\"role\":\"assistant\",\"content\":[{\"type\": \"text\", \"text\": \"Summarized previous interactions: "
                    + processTextDoc(summary)
                    + "\"}]}";

            // Update interactions: summary + remaining messages
            List<String> updatedInteractions = new ArrayList<>();
            updatedInteractions.add(summarizedInteraction);
            updatedInteractions.addAll(remainingMessages);

            // Update toolInteractions in context
            context.setToolInteractions(updatedInteractions);

            // Update parameters
            Map<String, String> parameters = context.getParameters();
            if (parameters == null) {
                parameters = new HashMap<>();
            }
            parameters.put(INTERACTIONS, ", " + String.join(", ", updatedInteractions));
            context.setParameters(parameters);

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

    private String extractSummaryFromResponse(MLTaskResponse response, ContextManagerContext context) {
        try {
            MLOutput output = response.getOutput();
            if (output instanceof ModelTensorOutput) {
                ModelTensorOutput tensorOutput = (ModelTensorOutput) output;
                List<ModelTensors> mlModelOutputs = tensorOutput.getMlModelOutputs();

                if (mlModelOutputs != null && !mlModelOutputs.isEmpty()) {
                    List<ModelTensor> tensors = mlModelOutputs.get(0).getMlModelTensors();
                    if (tensors != null && !tensors.isEmpty()) {
                        Map<String, ?> dataAsMap = tensors.get(0).getDataAsMap();

                        // Use LLM_RESPONSE_FILTER from agent configuration if available
                        Map<String, String> parameters = context.getParameters();
                        if (parameters != null
                            && parameters.containsKey(LLM_RESPONSE_FILTER)
                            && !parameters.get(LLM_RESPONSE_FILTER).isEmpty()) {
                            try {
                                String responseFilter = parameters.get(LLM_RESPONSE_FILTER);
                                Object filteredResponse = JsonPath.read(dataAsMap, responseFilter);
                                if (filteredResponse instanceof String) {
                                    String result = ((String) filteredResponse).trim();
                                    return result;
                                } else {
                                    String result = StringUtils.toJson(filteredResponse);
                                    return result;
                                }
                            } catch (PathNotFoundException e) {
                                log.debug("JSONPath filter not found, falling back to default parsing: {}", e.getMessage());
                                // Fall back to default parsing
                            } catch (Exception e) {
                                log.warn("Error applying JSONPath filter, falling back to default parsing: {}", e.getMessage());
                                // Fall back to default parsing
                            }
                        }

                        // Fallback to default parsing if no filter or filter fails
                        if (dataAsMap.size() == 1 && dataAsMap.containsKey("response")) {
                            Object responseObj = dataAsMap.get("response");
                            if (responseObj instanceof String) {
                                return ((String) responseObj).trim();
                            }
                        }

                        // Last resort: return JSON representation
                        return StringUtils.toJson(dataAsMap);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract summary from response", e);
        }

        return null; // Return null to indicate failure, let caller handle fallback
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
