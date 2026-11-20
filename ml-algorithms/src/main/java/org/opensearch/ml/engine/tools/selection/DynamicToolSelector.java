/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

import lombok.extern.log4j.Log4j2;

/**
 * Tool search for agents with many tools: the model sees a few pinned tools plus
 * {@link SearchToolsTool}, and pulls in the rest on demand.
 *
 * {@link #plan} never throws for an unsupported configuration; it returns
 * {@link ToolSelectionResult#notApplied}, which exposes every tool as if the feature were off.
 */
@Log4j2
public class DynamicToolSelector {

    static final String SKIP_DISABLED = "disabled";
    static final String SKIP_NO_TOOLS = "no_tools";
    static final String SKIP_POOL_WITHIN_LIMIT = "pool_within_limit";
    static final String SKIP_NAME_CONFLICT = "name_conflict";
    static final String SKIP_ALL_TOOLS_PINNED = "all_tools_pinned";

    private final ToolEmbeddingStore embeddingStore;

    public DynamicToolSelector(ToolEmbeddingStore embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    /**
     * Decide whether tool search applies to this execution. Makes no model call.
     *
     * @param toolsMap every tool configured on the agent, keyed by effective tool name
     * @param settings the effective configuration for this agent execution
     */
    public ToolSelectionResult plan(Map<String, Tool> toolsMap, ToolSelectionSettings settings) {
        int poolSize = toolsMap == null ? 0 : toolsMap.size();

        if (settings == null || !settings.isEnabled()) {
            return ToolSelectionResult.notApplied(poolSize, SKIP_DISABLED);
        }
        if (poolSize == 0) {
            return ToolSelectionResult.notApplied(poolSize, SKIP_NO_TOOLS);
        }
        if (poolSize <= settings.getMinPoolSize()) {
            // Below this size a search turn costs more latency than the prompt it saves.
            return ToolSelectionResult.notApplied(poolSize, SKIP_POOL_WITHIN_LIMIT);
        }
        if (toolsMap.containsKey(SearchToolsTool.NAME)) {
            // Never shadow a tool the agent owner configured under the same name.
            log.warn("Tool search skipped: the agent already has a tool named [{}]", SearchToolsTool.NAME);
            return ToolSelectionResult.notApplied(poolSize, SKIP_NAME_CONFLICT);
        }

        List<String> pinned = new ArrayList<>();
        for (String name : new LinkedHashSet<>(settings.getPinnedTools())) {
            if (toolsMap.containsKey(name)) {
                pinned.add(name);
            }
        }
        if (pinned.size() == poolSize) {
            return ToolSelectionResult.notApplied(poolSize, SKIP_ALL_TOOLS_PINNED);
        }
        return ToolSelectionResult.applied(pinned, poolSize);
    }

    /** Fills the embedding cache in the background so the first search is usually a cache hit. */
    public void warmUp(Map<String, Tool> searchableTools, ToolSelectionSettings settings, String tenantId) {
        if (searchableTools == null || searchableTools.isEmpty()) {
            return;
        }
        try {
            embeddingStore
                .getToolEmbeddings(
                    describe(searchableTools),
                    settings.getEmbeddingModelId(),
                    tenantId,
                    ActionListener
                        .wrap(
                            vectors -> log.debug("Tool search warm-up embedded {} of {} tools", vectors.size(), searchableTools.size()),
                            e -> log.warn("Tool search warm-up failed; the first search will embed on demand", e)
                        )
                );
        } catch (Exception e) {
            // Warm-up is best effort and runs on the request path, so it must not throw into it.
            log.warn("Tool search warm-up failed; the first search will embed on demand", e);
        }
    }

    /**
     * Rank {@code candidates} against a search phrase. Unlike {@link #plan}, fails the listener on
     * an embedding error.
     *
     * @param query      the phrase the model wrote
     * @param candidates tools not yet visible to the model
     * @param listener   receives up to {@code settings.getTopK()} names, best first
     */
    public void search(
        String query,
        Map<String, Tool> candidates,
        ToolSelectionSettings settings,
        String tenantId,
        ActionListener<List<String>> listener
    ) {
        if (candidates == null || candidates.isEmpty()) {
            listener.onResponse(List.of());
            return;
        }
        String modelId = settings.getEmbeddingModelId();
        embeddingStore.embedQuestion(query, modelId, tenantId, ActionListener.wrap(queryVector -> {
            embeddingStore.getToolEmbeddings(describe(candidates), modelId, tenantId, ActionListener.wrap(toolVectors -> {
                listener.onResponse(CosineRanker.topK(queryVector, toolVectors, settings.getTopK()));
            }, listener::onFailure));
        }, listener::onFailure));
    }

    /** Uses the effective description, after factory defaults and per-agent overrides. */
    private List<ToolDescriptor> describe(Map<String, Tool> tools) {
        List<ToolDescriptor> descriptors = new ArrayList<>(tools.size());
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            Tool tool = entry.getValue();
            descriptors.add(ToolDescriptor.of(entry.getKey(), tool == null ? null : tool.getDescription()));
        }
        return descriptors;
    }
}
