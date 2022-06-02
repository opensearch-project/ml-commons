/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.Getter;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.suppliers.CounterSupplier;

/**
 * This class is the main entry-point for access to the stats that the ML plugin keeps track of.
 */
public class MLStats {
    @Getter
    private Map<Enum, MLStat<?>> stats;
    private Map<FunctionName, Map<ActionName, Map<MLActionLevelStat, MLStat>>> algoStats;// {"kmeans":{"train":{"request_count":10}}}

    /**
     * Constructor
     *
     * @param stats Map of the stats that are to be kept
     */
    public MLStats(Map<Enum, MLStat<?>> stats) {
        this.stats = stats;
        this.algoStats = new ConcurrentHashMap<>();
    }

    /**
     * Get individual stat by stat name
     *
     * @param key Name of stat
     * @return ADStat
     * @throws IllegalArgumentException thrown on illegal statName
     */
    public MLStat<?> getStat(Enum key) throws IllegalArgumentException {
        if (!stats.keySet().contains(key)) {
            throw new IllegalArgumentException("Stat \"" + key + "\" does not exist");
        }
        return stats.get(key);
    }

    /**
     * Get stat or create counter stat if absent.
     * @param key stat key
     * @return existing MLStat or new MLStat
     */
    public MLStat<?> createCounterStatIfAbsent(Enum key) {
        return createStatIfAbsent(key, () -> new MLStat<>(false, new CounterSupplier()));
    }

    public MLStat<?> createCounterStatIfAbsent(FunctionName algoName, ActionName action, MLActionLevelStat stat) {
        Map<ActionName, Map<MLActionLevelStat, MLStat>> actionStats = algoStats.computeIfAbsent(algoName, it -> new ConcurrentHashMap<>());
        Map<MLActionLevelStat, MLStat> algoActionStats = actionStats.computeIfAbsent(action, it -> new ConcurrentHashMap<>());
        return createAlgoStatIfAbsent(algoActionStats, stat, () -> new MLStat<>(false, new CounterSupplier()));
    }

    public synchronized MLStat<?> createAlgoStatIfAbsent(
        Map<MLActionLevelStat, MLStat> algoActionStats,
        MLActionLevelStat key,
        Supplier<MLStat> supplier
    ) {
        return algoActionStats.computeIfAbsent(key, k -> supplier.get());
    }

    /**
     * Get stat or create if absent.
     * @param key stat key
     * @param supplier supplier to create MLStat
     * @return existing MLStat or new MLStat
     */
    public synchronized MLStat<?> createStatIfAbsent(Enum key, Supplier<MLStat> supplier) {
        return stats.computeIfAbsent(key, k -> supplier.get());
    }

    /**
     * Get a map of the stats that are kept at the node level
     *
     * @return Map of stats kept at the node level
     */
    public Map<Enum, MLStat<?>> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    /**
     * Get a map of the stats that are kept at the cluster level
     *
     * @return Map of stats kept at the cluster level
     */
    public Map<Enum, MLStat<?>> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<Enum, MLStat<?>> getClusterOrNodeStats(Boolean getClusterStats) {
        Map<Enum, MLStat<?>> statsMap = new HashMap<>();

        for (Map.Entry<Enum, MLStat<?>> entry : stats.entrySet()) {
            if (entry.getValue().isClusterLevel() == getClusterStats) {
                statsMap.put(entry.getKey(), entry.getValue());
            }
        }
        return statsMap;
    }

    /**
     * Get stats of specific algorithm.
     * @param algoName algorithm name
     * @return algorithm stats map: key is action name, value is action stats
     */
    public Map<ActionName, MLActionStats> getAlgorithmStats(FunctionName algoName) {
        if (!algoStats.containsKey(algoName)) {
            return null;
        }
        Map<ActionName, MLActionStats> algoActionStats = new HashMap<>();

        for (Map.Entry<ActionName, Map<MLActionLevelStat, MLStat>> entry : algoStats.get(algoName).entrySet()) {
            Map<MLActionLevelStat, Object> statsMap = new HashMap<>();
            for (Map.Entry<MLActionLevelStat, MLStat> state : entry.getValue().entrySet()) {
                statsMap.put(state.getKey(), state.getValue().getValue());
            }
            algoActionStats.put(entry.getKey(), new MLActionStats(statsMap));
        }
        return algoActionStats;
    }

    public FunctionName[] getAllAlgorithms() {
        return algoStats.keySet().toArray(new FunctionName[0]);
    }
}
