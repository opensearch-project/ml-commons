/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_REQUEST_COUNT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatsTests extends OpenSearchTestCase {
    private Map<Enum, MLStat<?>> statsMap;
    private MLStats mlStats;
    private MLClusterLevelStat clusterStatName1;
    private MLNodeLevelStat nodeStatName1;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        clusterStatName1 = MLClusterLevelStat.ML_MODEL_COUNT;

        nodeStatName1 = MLNodeLevelStat.ML_EXECUTING_TASK_COUNT;

        statsMap = new HashMap<Enum, MLStat<?>>() {
            {
                put(nodeStatName1, new MLStat<>(false, new CounterSupplier()));
                put(clusterStatName1, new MLStat<>(true, new CounterSupplier()));
            }
        };

        mlStats = new MLStats(statsMap);
    }

    public void testGetStats() {
        Map<Enum, MLStat<?>> stats = mlStats.getStats();

        Assert.assertEquals("getStats returns the incorrect number of stats", stats.size(), statsMap.size());

        for (Map.Entry<Enum, MLStat<?>> stat : stats.entrySet()) {
            Assert
                .assertTrue(
                    "getStats returns incorrect stats",
                    mlStats.getStats().containsKey(stat.getKey()) && mlStats.getStats().get(stat.getKey()) == stat.getValue()
                );
        }
    }

    public void testGetStat() {
        MLStat<?> stat = mlStats.getStat(clusterStatName1);

        Assert
            .assertTrue(
                "getStat returns incorrect stat",
                mlStats.getStats().containsKey(clusterStatName1) && mlStats.getStats().get(clusterStatName1) == stat
            );
    }

    public void testGetStatNoExisting() {
        MLNodeLevelStat wrongStat = MLNodeLevelStat.ML_JVM_HEAP_USAGE;
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Stat \"" + wrongStat + "\" does not exist");
        mlStats.getStat(wrongStat);
    }

    public void testCreateCounterStatIfAbsent() {
        MLStat<?> stat = mlStats.createCounterStatIfAbsent(MLNodeLevelStat.ML_FAILURE_COUNT);
        stat.increment();
        assertEquals(1L, stat.getValue());
    }

    public void testGetNodeStats() {
        Map<Enum, MLStat<?>> stats = mlStats.getStats();
        Set<MLStat<?>> nodeStats = new HashSet<>(mlStats.getNodeStats().values());

        for (MLStat<?> stat : stats.values()) {
            Assert
                .assertTrue(
                    "getNodeStats returns incorrect stat",
                    (stat.isClusterLevel() && !nodeStats.contains(stat)) || (!stat.isClusterLevel() && nodeStats.contains(stat))
                );
        }
    }

    public void testGetClusterStats() {
        Map<Enum, MLStat<?>> stats = mlStats.getStats();
        Set<MLStat<?>> clusterStats = new HashSet<>(mlStats.getClusterStats().values());

        for (MLStat<?> stat : stats.values()) {
            Assert
                .assertTrue(
                    "getClusterStats returns incorrect stat",
                    (stat.isClusterLevel() && clusterStats.contains(stat)) || (!stat.isClusterLevel() && !clusterStats.contains(stat))
                );
        }
    }

    public void testGetAlgorithmStats_Empty() {
        Map<ActionName, MLActionStats> algorithmStats = mlStats.getAlgorithmStats(FunctionName.KMEANS);
        assertNull(algorithmStats);
    }

    public void testGetAlgorithmStats() {
        MLStats stats = new MLStats(statsMap);
        MLStat<?> statCounter = stats.createCounterStatIfAbsent(FunctionName.KMEANS, ActionName.TRAIN, ML_ACTION_REQUEST_COUNT);
        statCounter.increment();
        Map<ActionName, MLActionStats> algorithmStats = stats.getAlgorithmStats(FunctionName.KMEANS);
        assertNotNull(algorithmStats);
        assertEquals(1l, algorithmStats.get(ActionName.TRAIN).getActionStat(ML_ACTION_REQUEST_COUNT));
    }

    public void testGetAllAlgorithms_Empty() {
        FunctionName[] allAlgorithms = mlStats.getAllAlgorithms();
        assertEquals(0, allAlgorithms.length);
    }

    public void testGetAllAlgorithms() {
        MLStats stats = new MLStats(statsMap);
        MLStat<?> statCounter = stats.createCounterStatIfAbsent(FunctionName.KMEANS, ActionName.TRAIN, ML_ACTION_REQUEST_COUNT);
        statCounter.increment();
        FunctionName[] allAlgorithms = stats.getAllAlgorithms();
        assertArrayEquals(new FunctionName[] { FunctionName.KMEANS }, allAlgorithms);
    }
}
