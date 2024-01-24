/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

// This test combined MLDeployControllerNodesRequestTest and MLDeployControllerNodeRequestTest together.
@RunWith(MockitoJUnitRunner.class)
public class MLDeployControllerNodesRequestTest {

    @Mock
    private DiscoveryNode localNode1;

    @Mock
    private DiscoveryNode localNode2;

    private MLDeployControllerNodeRequest deployControllerNodeRequestWithStringNodeIds;

    private MLDeployControllerNodeRequest deployControllerNodeRequestWithDiscoveryNodeIds;

    @Before
    public void setUp() throws Exception {

        String modelId = "testModelId";
        String[] stringNodeIds = { "nodeId1", "nodeId2", "nodeId3" };
        DiscoveryNode[] discoveryNodeIds = { localNode1, localNode2 };

        deployControllerNodeRequestWithStringNodeIds = new MLDeployControllerNodeRequest(
                new MLDeployControllerNodesRequest(stringNodeIds, modelId));
        deployControllerNodeRequestWithDiscoveryNodeIds = new MLDeployControllerNodeRequest(
                new MLDeployControllerNodesRequest(discoveryNodeIds, modelId));

    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        deployControllerNodeRequestWithStringNodeIds.writeTo(output);
        assertEquals("testModelId",
                deployControllerNodeRequestWithStringNodeIds.getDeployControllerNodesRequest().getModelId());

    }

    @Test
    public void testConstructorSerialization2() {
        assertEquals(2, deployControllerNodeRequestWithDiscoveryNodeIds.getDeployControllerNodesRequest()
                .concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        deployControllerNodeRequestWithStringNodeIds.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLDeployControllerNodeRequest parsedNodeRequest = new MLDeployControllerNodeRequest(streamInput);

        assertEquals(deployControllerNodeRequestWithStringNodeIds.getDeployControllerNodesRequest().getModelId(),
                parsedNodeRequest.getDeployControllerNodesRequest().getModelId());

    }
}
