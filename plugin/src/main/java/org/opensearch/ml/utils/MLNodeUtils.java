/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
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
