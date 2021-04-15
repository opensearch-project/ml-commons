/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.stats;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is the main entry-point for access to the stats that the ML plugin keeps track of.
 */
public class MLStats {
    @Getter
    private Map<String, MLStat<?>> stats;

    /**
     * Constructor
     *
     * @param stats Map of the stats that are to be kept
     */
    public MLStats(Map<String, MLStat<?>> stats) {
        this.stats = stats;
    }

    /**
     * Get individual stat by stat name
     *
     * @param key Name of stat
     * @return ADStat
     * @throws IllegalArgumentException thrown on illegal statName
     */
    public MLStat<?> getStat(String key) throws IllegalArgumentException {
        if (!stats.keySet().contains(key)) {
            throw new IllegalArgumentException("Stat=\"" + key + "\" does not exist");
        }
        return stats.get(key);
    }

    /**
     * Get a map of the stats that are kept at the node level
     *
     * @return Map of stats kept at the node level
     */
    public Map<String, MLStat<?>> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    /**
     * Get a map of the stats that are kept at the cluster level
     *
     * @return Map of stats kept at the cluster level
     */
    public Map<String, MLStat<?>> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<String, MLStat<?>> getClusterOrNodeStats(Boolean getClusterStats) {
        Map<String, MLStat<?>> statsMap = new HashMap<>();

        for (Map.Entry<String, MLStat<?>> entry : stats.entrySet()) {
            if (entry.getValue().getClusterLevel() == getClusterStats) {
                statsMap.put(entry.getKey(), entry.getValue());
            }
        }
        return statsMap;
    }
}
