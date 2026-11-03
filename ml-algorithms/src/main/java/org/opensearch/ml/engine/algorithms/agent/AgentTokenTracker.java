/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.agent.TokenUsage;

/**
 * Tracks token usage across multiple LLM calls during agent execution.
 * Maintains both per-turn usage (each individual LLM call) and per-model
 * aggregated usage (total across all calls to each model).
 */
public class AgentTokenTracker {

    // Field name constants
    public static final String TOKEN_USAGE = "token_usage";
    public static final String PER_MODEL_USAGE = "per_model_usage";
    public static final String PER_TURN_USAGE = "per_turn_usage";
    public static final String MODEL_NAME = "model_name";
    public static final String MODEL_URL = "model_url";
    public static final String MODEL_ID = "model_id";
    public static final String CALL_COUNT = "call_count";
    public static final String TURN = "turn";
    public static final String IS_SUB_AGENT_FIELD = "_is_sub_agent";

    // Track each individual LLM call
    private final List<Map<String, Object>> perTurnUsage;

    // Aggregated by model ID
    private final Map<String, ModelUsageAggregation> perModelUsage;

    // Model metadata: modelId -> {modelUrl, modelName}
    private final Map<String, ModelMetadata> modelMetadataMap;

    // Current turn counter
    private int turnCounter;

    // Whether this tracker belongs to a sub-agent (e.g., ReAct executor inside PER).
    // When true, token usage logging is suppressed to avoid double-logging —
    // the parent agent logs the merged totals instead.
    private boolean subAgent;

    public AgentTokenTracker() {
        this.perTurnUsage = new ArrayList<>();
        this.perModelUsage = new HashMap<>();
        this.modelMetadataMap = new HashMap<>();
        this.turnCounter = 0;
        this.subAgent = false;
    }

    public boolean isSubAgent() {
        return subAgent;
    }

    public void setSubAgent(boolean subAgent) {
        this.subAgent = subAgent;
    }

    /**
     * Sets model metadata for enriching token usage data.
     * Should be called when model details are resolved.
     *
     * @param modelId The model ID
     * @param modelUrl The resolved model URL
     * @param modelName The model name (can be same as modelId or modelUrl)
     */
    public void setModelMetadata(String modelId, String modelUrl, String modelName) {
        if (modelId != null) {
            modelMetadataMap.put(modelId, new ModelMetadata(modelUrl, modelName));
        }
    }

    /**
     * Records token usage for a single LLM call
     *
     * @param modelId The model ID
     * @param usage The token usage from the LLM response
     */
    public void recordTurn(String modelId, TokenUsage usage) {
        if (modelId == null || usage == null) {
            return;
        }

        turnCounter++;

        // Get model metadata (or use defaults if not set)
        ModelMetadata modelMetadata = modelMetadataMap.getOrDefault(modelId, new ModelMetadata(modelId, modelId));

        // Add to per-turn list
        Map<String, Object> turnData = new HashMap<>();
        turnData.put(TURN, turnCounter);
        turnData.put(MODEL_NAME, modelMetadata.getModelName());
        turnData.put(MODEL_URL, modelMetadata.getModelUrl());
        turnData.put(MODEL_ID, modelId);
        turnData.putAll(usage.toMap());
        perTurnUsage.add(turnData);

        // Update per-model aggregation (keyed by model ID)
        perModelUsage.computeIfAbsent(modelId, k -> new ModelUsageAggregation(modelMetadata)).addUsage(usage);
    }

    /**
     * Returns the complete token usage data structure for inclusion in agent response
     *
     * @return Map with "per_model_usage" and "per_turn_usage" keys
     */
    public Map<String, Object> toOutputMap() {
        Map<String, Object> output = new HashMap<>();

        // Build per_model_usage list
        List<Map<String, Object>> perModelList = new ArrayList<>();
        for (Map.Entry<String, ModelUsageAggregation> entry : perModelUsage.entrySet()) {
            Map<String, Object> modelData = new HashMap<>();
            ModelMetadata modelMetadata = entry.getValue().getMetadata();
            modelData.put(MODEL_NAME, modelMetadata.getModelName());
            modelData.put(MODEL_URL, modelMetadata.getModelUrl());
            modelData.put(MODEL_ID, entry.getKey());
            modelData.putAll(entry.getValue().getAggregatedUsage().toMap());
            modelData.put(CALL_COUNT, entry.getValue().getCallCount());
            perModelList.add(modelData);
        }

        output.put(PER_MODEL_USAGE, perModelList);
        output.put(PER_TURN_USAGE, perTurnUsage);

        return output;
    }

    /**
     * Merges pre-aggregated token usage data from a sub-agent response.
     * The sub-agent (e.g., Chat/ReAct executor) has already tracked its own tokens
     * and returns them as aggregated per_turn_usage and per_model_usage.
     *
     * @param tokenUsageMap the token_usage dataAsMap from the sub-agent response tensor
     */
    @SuppressWarnings("unchecked")
    public void mergeSubAgentUsage(Map<String, Object> tokenUsageMap) {
        if (tokenUsageMap == null) {
            return;
        }

        // Merge per-turn usage: re-number turns sequentially
        Object perTurnObj = tokenUsageMap.get(PER_TURN_USAGE);
        if (perTurnObj instanceof List) {
            List<Map<String, Object>> subTurns = (List<Map<String, Object>>) perTurnObj;
            for (Map<String, Object> subTurn : subTurns) {
                turnCounter++;
                Map<String, Object> mergedTurn = new HashMap<>(subTurn);
                mergedTurn.put(TURN, turnCounter);
                perTurnUsage.add(mergedTurn);
            }
        }

        // Merge per-model usage: aggregate into existing model data
        Object perModelObj = tokenUsageMap.get(PER_MODEL_USAGE);
        if (perModelObj instanceof List) {
            List<Map<String, Object>> subModels = (List<Map<String, Object>>) perModelObj;
            for (Map<String, Object> subModel : subModels) {
                String modelId = (String) subModel.get(MODEL_ID);
                if (modelId == null) {
                    continue;
                }

                String modelUrl = (String) subModel.getOrDefault(MODEL_URL, modelId);
                String modelName = (String) subModel.getOrDefault(MODEL_NAME, modelId);

                TokenUsage subUsage = TokenUsage
                    .builder()
                    .inputTokens(getLongFromMap(subModel, TokenUsage.INPUT_TOKENS))
                    .outputTokens(getLongFromMap(subModel, TokenUsage.OUTPUT_TOKENS))
                    .totalTokens(getLongFromMap(subModel, TokenUsage.TOTAL_TOKENS))
                    .cacheReadInputTokens(getLongFromMap(subModel, TokenUsage.CACHE_READ_INPUT_TOKENS))
                    .cacheCreationInputTokens(getLongFromMap(subModel, TokenUsage.CACHE_CREATION_INPUT_TOKENS))
                    .reasoningTokens(getLongFromMap(subModel, TokenUsage.REASONING_TOKENS))
                    .build();

                int subCallCount = subModel.containsKey(CALL_COUNT) ? ((Number) subModel.get(CALL_COUNT)).intValue() : 1;

                if (!modelMetadataMap.containsKey(modelId)) {
                    modelMetadataMap.put(modelId, new ModelMetadata(modelUrl, modelName));
                }

                ModelMetadata modelMetadata = modelMetadataMap.getOrDefault(modelId, new ModelMetadata(modelUrl, modelName));
                perModelUsage
                    .computeIfAbsent(modelId, k -> new ModelUsageAggregation(modelMetadata))
                    .mergeAggregated(subUsage, subCallCount);
            }
        }
    }

    private static Long getLongFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * Checks if any token usage has been recorded
     *
     * @return true if at least one turn has been recorded
     */
    public boolean hasUsage() {
        return !perTurnUsage.isEmpty();
    }

    /**
     * Internal class to track aggregated usage for a specific model
     */
    private static class ModelUsageAggregation {
        private TokenUsage aggregatedUsage;
        private int callCount;
        private ModelMetadata modelMetadata;

        public ModelUsageAggregation(ModelMetadata modelMetadata) {
            this.aggregatedUsage = TokenUsage.builder().build();
            this.callCount = 0;
            this.modelMetadata = modelMetadata;
        }

        public void addUsage(TokenUsage usage) {
            this.aggregatedUsage = this.aggregatedUsage.addTokens(usage);
            this.callCount++;
        }

        public void mergeAggregated(TokenUsage aggregatedUsage, int callCount) {
            this.aggregatedUsage = this.aggregatedUsage.addTokens(aggregatedUsage);
            this.callCount += callCount;
        }

        public TokenUsage getAggregatedUsage() {
            return aggregatedUsage;
        }

        public int getCallCount() {
            return callCount;
        }

        public ModelMetadata getMetadata() {
            return modelMetadata;
        }
    }

    /**
     * Internal class to store model metadata
     */
    private static class ModelMetadata {
        private final String modelUrl;
        private final String modelName;

        public ModelMetadata(String modelUrl, String modelName) {
            this.modelUrl = modelUrl != null ? modelUrl : "";
            this.modelName = modelName != null ? modelName : "";
        }

        public String getModelUrl() {
            return modelUrl;
        }

        public String getModelName() {
            return modelName;
        }
    }
}
