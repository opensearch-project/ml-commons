/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import lombok.experimental.UtilityClass;

import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.ml.plugin.MachineLearningPlugin;

@UtilityClass
public class MLNodeUtils {
    public boolean isMLNode(DiscoveryNode node) {
        return node.getRoles().stream().anyMatch(role -> role.roleName().equalsIgnoreCase(MachineLearningPlugin.ML_ROLE.roleName()));
    }
}
