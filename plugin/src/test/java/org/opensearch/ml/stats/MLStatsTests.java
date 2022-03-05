/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatsTests extends OpenSearchTestCase {
    private Map<String, MLStat<?>> statsMap;
    private MLStats mlStats;
    private String clusterStatName1;
    private String nodeStatName1;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        clusterStatName1 = "clusterStat1";

        nodeStatName1 = "nodeStat1";

        statsMap = new HashMap<String, MLStat<?>>() {
            {
                put(nodeStatName1, new MLStat<>(false, new CounterSupplier()));
                put(clusterStatName1, new MLStat<>(true, new CounterSupplier()));
            }
        };

        mlStats = new MLStats(statsMap);
    }

    public void testGetStats() {
        Map<String, MLStat<?>> stats = mlStats.getStats();

        Assert.assertEquals("getStats returns the incorrect number of stats", stats.size(), statsMap.size());

        for (Map.Entry<String, MLStat<?>> stat : stats.entrySet()) {
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
        String wrongStat = randomAlphaOfLength(10);
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Stat \"" + wrongStat + "\" does not exist");
        mlStats.getStat(wrongStat);
    }

    public void testCreateCounterStatIfAbsent() {
        MLStat<?> stat = mlStats.createCounterStatIfAbsent("dummy stat name");
        stat.increment();
        assertEquals(1L, stat.getValue());
    }

    public void testGetNodeStats() {
        Map<String, MLStat<?>> stats = mlStats.getStats();
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
        Map<String, MLStat<?>> stats = mlStats.getStats();
        Set<MLStat<?>> clusterStats = new HashSet<>(mlStats.getClusterStats().values());

        for (MLStat<?> stat : stats.values()) {
            Assert
                .assertTrue(
                    "getClusterStats returns incorrect stat",
                    (stat.isClusterLevel() && clusterStats.contains(stat)) || (!stat.isClusterLevel() && !clusterStats.contains(stat))
                );
        }
    }
}
