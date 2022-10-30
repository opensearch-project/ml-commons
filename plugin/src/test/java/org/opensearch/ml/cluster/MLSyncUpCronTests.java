/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLSyncUpCronTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private DiscoveryNodeHelper nodeHelper;

    private DiscoveryNode mlNode1;
    private DiscoveryNode mlNode2;
    private MLSyncUpCron syncUpCron;

    private final String mlNode1Id = "mlNode1";
    private final String mlNode2Id = "mlNode2";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlNode1 = new DiscoveryNode(mlNode1Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        mlNode2 = new DiscoveryNode(mlNode2Id, buildNewFakeTransportAddress(), emptyMap(), ImmutableSet.of(ML_ROLE), Version.CURRENT);
        syncUpCron = new MLSyncUpCron(client, nodeHelper);
    }

    public void testRun() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks();

        syncUpCron.run();
        verify(client, times(2)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun_NoLoadedModel() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks();

        syncUpCron.run();
        verify(client, times(2)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    public void testRun_Failure() {
        DiscoveryNode[] allNodes = new DiscoveryNode[] {};
        when(nodeHelper.getAllNodes()).thenReturn(allNodes);
        mockSyncUp_GatherRunningTasks_Failure();

        syncUpCron.run();
        verify(client, times(1)).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    private void mockSyncUp_GatherRunningTasks() {
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            List<MLSyncUpNodeResponse> nodeResponses = new ArrayList<>();
            String[] loadedModelIds = new String[] { randomAlphaOfLength(10) };
            String[] runningLoadModelTaskIds = new String[] { randomAlphaOfLength(10) };
            nodeResponses.add(new MLSyncUpNodeResponse(mlNode1, "ok", loadedModelIds, runningLoadModelTaskIds));
            MLSyncUpNodesResponse response = new MLSyncUpNodesResponse(ClusterName.DEFAULT, nodeResponses, Arrays.asList());
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }

    private void mockSyncUp_GatherRunningTasks_Failure() {
        doAnswer(invocation -> {
            ActionListener<MLSyncUpNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("failed to get running tasks"));
            return null;
        }).when(client).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
    }
}
