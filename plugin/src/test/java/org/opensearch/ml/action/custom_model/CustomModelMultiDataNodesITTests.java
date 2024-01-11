/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom_model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 3)
public class CustomModelMultiDataNodesITTests extends CustomModelITTests {

    @Ignore
    public void testCustomModelWorkflow() throws InterruptedException {
        DiscoveryNodes nodes = clusterService().state().getNodes();
        String id = clusterService().localNode().getId();
        List<String> remoteDataNodes = new ArrayList<>();
        for (DiscoveryNode node : nodes) {
            if (node.isDataNode() && !id.equals(node.getId())) {
                remoteDataNodes.add(node.getId());
            }
        }
        assertTrue(remoteDataNodes.size() >= 2);

        // We can only load model on one data node on DJL under Opensearch integ test framework
        testTextEmbeddingModel(Set.of(remoteDataNodes.get(0)));
        testKMeans(Set.of(remoteDataNodes.get(1)));
    }

}
