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

// This test combined MLDeployModelControllerNodesRequestTest and MLDeployModelControllerNodeRequestTest together.
@RunWith(MockitoJUnitRunner.class)
public class MLDeployModelControllerNodesRequestTest {

    @Mock
    private DiscoveryNode localNode1;

    @Mock
    private DiscoveryNode localNode2;

    private MLDeployModelControllerNodeRequest deployModelControllerNodeRequestWithStringNodeIds;

    private MLDeployModelControllerNodeRequest deployModelControllerNodeRequestWithDiscoveryNodeIds;

    @Before
    public void setUp() throws Exception {

        String modelId = "testModelId";
        String[] stringNodeIds = {"nodeId1", "nodeId2", "nodeId3"};
        DiscoveryNode[] discoveryNodeIds = {localNode1, localNode2};

        deployModelControllerNodeRequestWithStringNodeIds = new MLDeployModelControllerNodeRequest(
                new MLDeployModelControllerNodesRequest(stringNodeIds, modelId)
        );
        deployModelControllerNodeRequestWithDiscoveryNodeIds = new MLDeployModelControllerNodeRequest(
                new MLDeployModelControllerNodesRequest(discoveryNodeIds, modelId)
        );

    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        deployModelControllerNodeRequestWithStringNodeIds.writeTo(output);
        assertEquals("testModelId", deployModelControllerNodeRequestWithStringNodeIds.getDeployModelControllerNodesRequest().getModelId());

    }

    @Test
    public void testConstructorSerialization2() {
        assertEquals(2, deployModelControllerNodeRequestWithDiscoveryNodeIds.getDeployModelControllerNodesRequest().concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        deployModelControllerNodeRequestWithStringNodeIds.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLDeployModelControllerNodeRequest parsedNodeRequest = new MLDeployModelControllerNodeRequest(streamInput);

        assertEquals(deployModelControllerNodeRequestWithStringNodeIds.getDeployModelControllerNodesRequest().getModelId(),
                parsedNodeRequest.getDeployModelControllerNodesRequest().getModelId());

    }
}
