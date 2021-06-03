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

import static java.util.Collections.emptyMap;

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.test.OpenSearchTestCase;

public class MLNodeUtilsTests extends OpenSearchTestCase {
    @Test
    public void testIsMLNode() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        roleSet.add(DiscoveryNodeRole.INGEST_ROLE);
        DiscoveryNode normalNode = new DiscoveryNode("Normal node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertFalse(MLNodeUtils.isMLNode(normalNode));

        roleSet.add(MachineLearningPlugin.ML_ROLE);
        DiscoveryNode mlNode = new DiscoveryNode("ML node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertTrue(MLNodeUtils.isMLNode(mlNode));
    }
}
