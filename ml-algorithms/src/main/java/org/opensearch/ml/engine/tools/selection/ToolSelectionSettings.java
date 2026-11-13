/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT;

import java.util.List;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSelectionConfig;

import lombok.Getter;

/**
 * The tool-search configuration in force for one agent execution: cluster settings plus the
 * agent's own configuration, with built-in defaults for anything unset.
 *
 * @see MLToolSelectionConfig for the per-agent half of this
 */
@Getter
public class ToolSelectionSettings {

    /** How many tools one search_tools call adds. */
    static final int DEFAULT_TOP_K = 5;
    /** Agents with this many tools or fewer see all of them: below it, a search turn costs more than it saves. */
    static final int DEFAULT_MIN_POOL_SIZE = 10;
    /** Three searches at the default top_k. */
    static final int DEFAULT_MAX_DYNAMIC_TOOLS = 15;

    private final boolean enabled;
    private final String embeddingModelId;
    private final List<String> pinnedTools;
    private final int topK;
    private final int minPoolSize;
    private final int maxDynamicTools;
    private final TimeValue searchTimeout;

    private ToolSelectionSettings(
        boolean enabled,
        String embeddingModelId,
        List<String> pinnedTools,
        int topK,
        int minPoolSize,
        int maxDynamicTools,
        TimeValue searchTimeout
    ) {
        this.enabled = enabled;
        this.embeddingModelId = embeddingModelId;
        this.pinnedTools = List.copyOf(pinnedTools);
        this.topK = topK;
        this.minPoolSize = minPoolSize;
        this.maxDynamicTools = maxDynamicTools;
        this.searchTimeout = searchTimeout;
    }

    /**
     * Resolve the effective configuration for one request. Tool search applies only when the
     * cluster switch is on, the agent opted in, and an embedding model is configured.
     *
     * @param clusterService source of the cluster-level settings
     * @param mlAgent        the agent being executed; may be null, in which case tool search is off
     */
    public static ToolSelectionSettings resolve(ClusterService clusterService, MLAgent mlAgent) {
        ClusterSettings clusterSettings = clusterService.getClusterSettings();
        boolean clusterEnabled = clusterSettings.get(ML_COMMONS_TOOL_SELECTION_ENABLED);
        String embeddingModelId = clusterSettings.get(ML_COMMONS_TOOL_SELECTION_EMBEDDING_MODEL_ID);
        TimeValue searchTimeout = clusterSettings.get(ML_COMMONS_TOOL_SELECTION_SEARCH_TIMEOUT);

        MLToolSelectionConfig config = mlAgent == null ? null : mlAgent.getToolSelection();
        if (config == null) {
            config = MLToolSelectionConfig.builder().build();
        }
        if (config.getEmbeddingModelId() != null) {
            embeddingModelId = config.getEmbeddingModelId();
        }

        boolean optedIn = Boolean.TRUE.equals(config.getEnabled());
        boolean hasModel = embeddingModelId != null && !embeddingModelId.isBlank();
        return new ToolSelectionSettings(
            clusterEnabled && optedIn && hasModel,
            embeddingModelId,
            config.getPinnedTools() == null ? List.of() : config.getPinnedTools(),
            orDefault(config.getTopK(), DEFAULT_TOP_K),
            orDefault(config.getMinPoolSize(), DEFAULT_MIN_POOL_SIZE),
            orDefault(config.getMaxDynamicTools(), DEFAULT_MAX_DYNAMIC_TOOLS),
            searchTimeout
        );
    }

    private static int orDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
