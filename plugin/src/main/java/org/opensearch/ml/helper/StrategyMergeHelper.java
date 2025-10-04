/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;

import lombok.extern.log4j.Log4j2;

/**
 * Helper class for merging memory strategies during container updates.
 * Handles both updating existing strategies and adding new ones.
 */
@Log4j2
public class StrategyMergeHelper {

    /**
     * Merges update strategies with existing strategies.
     *
     * Rules:
     * - If update strategy has ID: finds and updates matching existing strategy
     * - If update strategy has no ID: creates new strategy with auto-generated ID
     * - For updates with ID: only non-null fields are updated
     * - Strategy type cannot be changed
     * - New strategies without explicit enabled default to true
     *
     * @param existing List of existing strategies
     * @param updates List of strategy updates to apply
     * @return Merged list of strategies
     * @throws OpenSearchStatusException if strategy ID not found (404) or type change attempted (400)
     */
    public static List<MemoryStrategy> mergeStrategies(List<MemoryStrategy> existing, List<MemoryStrategy> updates) {
        if (existing == null) {
            existing = new ArrayList<>();
        }
        if (updates == null || updates.isEmpty()) {
            return existing;
        }

        // Create map of existing strategies by ID for efficient lookup
        Map<String, MemoryStrategy> strategyMap = existing.stream().collect(Collectors.toMap(MemoryStrategy::getId, Function.identity()));

        // Process each update
        for (MemoryStrategy update : updates) {
            if (update.getId() != null && !update.getId().trim().isEmpty()) {
                // Update existing strategy
                MemoryStrategy existingStrategy = strategyMap.get(update.getId());

                if (existingStrategy == null) {
                    log.error("Strategy with id {} not found", update.getId());
                    throw new OpenSearchStatusException("Strategy with id " + update.getId() + " not found", RestStatus.NOT_FOUND);
                }

                // Validate type not changed
                if (update.getType() != null && !update.getType().equals(existingStrategy.getType())) {
                    log
                        .error(
                            "Attempt to change strategy type from {} to {} for strategy {}",
                            existingStrategy.getType(),
                            update.getType(),
                            update.getId()
                        );
                    throw new IllegalArgumentException(
                        "Cannot change strategy type from "
                            + existingStrategy.getType()
                            + " to "
                            + update.getType()
                            + " for strategy "
                            + update.getId()
                    );
                }

                // Merge fields (only update non-null fields)
                if (update.getEnabled() != null) {
                    existingStrategy.setEnabled(update.getEnabled());
                }
                if (update.getNamespace() != null) {
                    existingStrategy.setNamespace(update.getNamespace());
                }
                if (update.getStrategyConfig() != null) {
                    existingStrategy.setStrategyConfig(update.getStrategyConfig());
                }

                log.debug("Updated strategy with id: {}", update.getId());
            } else {
                // Add new strategy with generated ID
                String newId = MemoryStrategy.generateStrategyId(update.getType());
                update.setId(newId);

                // Set enabled to true if not specified (default for new strategies)
                if (update.getEnabled() == null) {
                    update.setEnabled(true);
                }

                strategyMap.put(newId, update);
                log.debug("Added new strategy with id: {}", newId);
            }
        }

        return new ArrayList<>(strategyMap.values());
    }
}
