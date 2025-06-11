/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.utils.MLNodeUtils.parseArrayField;
import static org.opensearch.ml.utils.MLNodeUtils.parseField;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLStatsInput implements ToXContentObject, Writeable {
    public static final String TARGET_STAT_LEVEL = "target_stat_levels";
    public static final String CLUSTER_LEVEL_STATS = "cluster_level_stats";
    public static final String NODE_LEVEL_STATS = "node_level_stats";
    public static final String ACTION_LEVEL_STATS = "action_level_stats";
    public static final String NODE_IDS = "node_ids";
    public static final String ALGORITHMS = "algorithms";
    public static final String MODELS = "models";
    public static final String ACTIONS = "actions";

    /**
     * Retrieve which stat levels, could be one or multiple stats.
     * If no stats set, will not retrieve any stats.
     */
    private EnumSet<MLStatLevel> targetStatLevels;
    /**
     * Which cluster level stats will be retrieved.
     */
    private EnumSet<MLClusterLevelStat> clusterLevelStats;
    /**
     * Which node level stats will be retrieved.
     */
    private EnumSet<MLNodeLevelStat> nodeLevelStats;
    /**
     * Which action level stats will be retrieved.
     */
    private EnumSet<MLActionLevelStat> actionLevelStats;
    /**
     * Which node's stats will be retrieved.
     */
    private Set<String> nodeIds;
    /**
     * Which algorithm's stats will be retrieved.
     */
    private EnumSet<FunctionName> algorithms;
    /**
     * Which model's stats will be retrieved.
     */
    private Set<String> models;

    /**
     * Which action's stats will be retrieved.
     */
    private EnumSet<ActionName> actions;

    /**
     * Constructor
     * @param targetStatLevels target stat levels which will be retrieved
     * @param clusterLevelStats cluster level stats which will be retrieved
     * @param nodeLevelStats node level stats which will be retrieved
     * @param actionLevelStats action level stats which will be retrieved
     * @param nodeIds retrieve stats on these nodes
     * @param algorithms retrieve stats for which algorithms
     * @param models retrieve stats for which models
     * @param actions retrieve stats for which actions
     */
    @Builder
    public MLStatsInput(
        EnumSet<MLStatLevel> targetStatLevels,
        EnumSet<MLClusterLevelStat> clusterLevelStats,
        EnumSet<MLNodeLevelStat> nodeLevelStats,
        EnumSet<MLActionLevelStat> actionLevelStats,
        Set<String> nodeIds,
        EnumSet<FunctionName> algorithms,
        Set<String> models,
        EnumSet<ActionName> actions
    ) {
        this.targetStatLevels = targetStatLevels;
        this.clusterLevelStats = clusterLevelStats;
        this.nodeLevelStats = nodeLevelStats;
        this.actionLevelStats = actionLevelStats;
        this.nodeIds = nodeIds;
        this.algorithms = algorithms;
        this.models = models;
        this.actions = actions;
    }

    public MLStatsInput() {
        this.targetStatLevels = EnumSet.noneOf(MLStatLevel.class);
        this.clusterLevelStats = EnumSet.noneOf(MLClusterLevelStat.class);
        this.nodeLevelStats = EnumSet.noneOf(MLNodeLevelStat.class);
        this.actionLevelStats = EnumSet.noneOf(MLActionLevelStat.class);
        this.nodeIds = new HashSet<>();
        this.algorithms = EnumSet.noneOf(FunctionName.class);
        this.models = new HashSet<>();
        this.actions = EnumSet.noneOf(ActionName.class);
    }

    public MLStatsInput(StreamInput input) throws IOException {
        targetStatLevels = input.readOptionalEnumSet(MLStatLevel.class);
        clusterLevelStats = input.readOptionalEnumSet(MLClusterLevelStat.class);
        nodeLevelStats = input.readOptionalEnumSet(MLNodeLevelStat.class);
        actionLevelStats = input.readOptionalEnumSet(MLActionLevelStat.class);
        nodeIds = input.readBoolean() ? new HashSet<>(input.readStringList()) : new HashSet<>();
        models = input.readBoolean() ? new HashSet<>(input.readStringList()) : new HashSet<>();
        algorithms = input.readOptionalEnumSet(FunctionName.class);
        actions = input.readOptionalEnumSet(ActionName.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalEnumSet(targetStatLevels);
        out.writeOptionalEnumSet(clusterLevelStats);
        out.writeOptionalEnumSet(nodeLevelStats);
        out.writeOptionalEnumSet(actionLevelStats);
        out.writeOptionalStringCollection(nodeIds);
        out.writeOptionalStringCollection(models);
        out.writeOptionalEnumSet(algorithms);
        out.writeOptionalEnumSet(actions);
    }

    public static MLStatsInput parse(XContentParser parser) throws IOException {
        EnumSet<MLStatLevel> targetStatLevels = EnumSet.noneOf(MLStatLevel.class);
        EnumSet<MLClusterLevelStat> clusterLevelStats = EnumSet.noneOf(MLClusterLevelStat.class);
        EnumSet<MLNodeLevelStat> nodeLevelStats = EnumSet.noneOf(MLNodeLevelStat.class);
        EnumSet<MLActionLevelStat> actionLevelStats = EnumSet.noneOf(MLActionLevelStat.class);
        Set<String> nodeIds = new HashSet<>();
        Set<String> models = new HashSet<>();
        EnumSet<FunctionName> algorithms = EnumSet.noneOf(FunctionName.class);
        EnumSet<ActionName> actions = EnumSet.noneOf(ActionName.class);

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TARGET_STAT_LEVEL:
                    parseField(parser, targetStatLevels, input -> MLStatLevel.from(input.toUpperCase(Locale.ROOT)), MLStatLevel.class);
                    break;
                case CLUSTER_LEVEL_STATS:
                    parseField(
                        parser,
                        clusterLevelStats,
                        input -> MLClusterLevelStat.from(input.toUpperCase(Locale.ROOT)),
                        MLClusterLevelStat.class
                    );
                    break;
                case NODE_LEVEL_STATS:
                    parseField(
                        parser,
                        nodeLevelStats,
                        input -> MLNodeLevelStat.from(input.toUpperCase(Locale.ROOT)),
                        MLNodeLevelStat.class
                    );
                    break;
                case ACTION_LEVEL_STATS:
                    parseField(
                        parser,
                        actionLevelStats,
                        input -> MLActionLevelStat.from(input.toUpperCase(Locale.ROOT)),
                        MLActionLevelStat.class
                    );
                    break;
                case NODE_IDS:
                    parseArrayField(parser, nodeIds);
                    break;
                case ALGORITHMS:
                    parseField(parser, algorithms, input -> FunctionName.from(input.toUpperCase(Locale.ROOT)), FunctionName.class);
                    break;
                case MODELS:
                    parseArrayField(parser, models);
                    break;
                case ACTIONS:
                    parseField(parser, actions, input -> ActionName.from(input.toUpperCase(Locale.ROOT)), ActionName.class);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLStatsInput
            .builder()
            .targetStatLevels(targetStatLevels)
            .clusterLevelStats(clusterLevelStats)
            .nodeLevelStats(nodeLevelStats)
            .actionLevelStats(actionLevelStats)
            .nodeIds(nodeIds)
            .algorithms(algorithms)
            .models(models)
            .actions(actions)
            .build();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (targetStatLevels != null) {
            builder.field(TARGET_STAT_LEVEL, targetStatLevels);
        }
        if (clusterLevelStats != null) {
            builder.field(CLUSTER_LEVEL_STATS, clusterLevelStats);
        }
        if (nodeLevelStats != null) {
            builder.field(NODE_LEVEL_STATS, nodeLevelStats);
        }
        if (actionLevelStats != null) {
            builder.field(ACTION_LEVEL_STATS, actionLevelStats);
        }
        if (nodeIds != null) {
            builder.field(NODE_IDS, nodeIds);
        }
        if (algorithms != null) {
            builder.field(ALGORITHMS, algorithms);
        }
        if (models != null) {
            builder.field(MODELS, models);
        }
        if (actions != null) {
            builder.field(ACTIONS, actions);
        }
        builder.endObject();
        return builder;
    }

    public boolean retrieveAllClusterLevelStats() {
        return clusterLevelStats == null || clusterLevelStats.size() == 0;
    }

    public boolean retrieveAllNodeLevelStats() {
        return nodeLevelStats == null || nodeLevelStats.size() == 0;
    }

    public boolean retrieveAllActionLevelStats() {
        return actionLevelStats == null || actionLevelStats.size() == 0;
    }

    public boolean retrieveStatsOnAllNodes() {
        return nodeIds == null || nodeIds.size() == 0;
    }

    public boolean retrieveStatsForAllAlgos() {
        return algorithms == null || algorithms.size() == 0;
    }

    public boolean retrieveStatsForAllModels() {
        return models == null || models.size() == 0;
    }

    public boolean retrieveStatsForAlgo(FunctionName algoName) {
        return retrieveStatsForAllAlgos() || algorithms.contains(algoName);
    }

    public boolean retrieveStatsForModel(String modelId) {
        return retrieveStatsForAllModels() || models.contains(modelId);
    }

    public boolean retrieveStatsForAction(ActionName actionName) {
        return retrieveStatsForAllActions() || actions.contains(actionName);
    }

    public boolean retrieveStatsForAllActions() {
        return actions == null || actions.size() == 0;
    }

    public boolean retrieveStat(Enum<?> key) {
        if (key instanceof MLClusterLevelStat) {
            return retrieveAllClusterLevelStats() || clusterLevelStats.contains(key);
        }
        if (key instanceof MLNodeLevelStat) {
            return retrieveAllNodeLevelStats() || nodeLevelStats.contains(key);
        }
        if (key instanceof MLActionLevelStat) {
            return retrieveAllActionLevelStats() || actionLevelStats.contains(key);
        }
        return false;
    }

    public boolean onlyRetrieveClusterLevelStats() {
        if (targetStatLevels == null || targetStatLevels.size() == 0) {
            return false;
        }
        return !targetStatLevels.contains(MLStatLevel.NODE)
            && !targetStatLevels.contains(MLStatLevel.ALGORITHM)
            && !targetStatLevels.contains(MLStatLevel.MODEL)
            && !targetStatLevels.contains(MLStatLevel.ACTION);
    }

    public boolean includeAlgoStats() {
        return targetStatLevels.contains(MLStatLevel.ALGORITHM) || targetStatLevels.contains(MLStatLevel.ACTION);
    }

    public boolean includeModelStats() {
        return targetStatLevels.contains(MLStatLevel.MODEL) || targetStatLevels.contains(MLStatLevel.ACTION);
    }
}
