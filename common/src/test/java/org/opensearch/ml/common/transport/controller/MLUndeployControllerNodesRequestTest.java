/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import static org.junit.Assert.assertEquals;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

// This test combined MLUndeployControllerNodesRequestTest and MLUndeployControllerNodeRequestTest together.
@RunWith(MockitoJUnitRunner.class)
public class MLUndeployControllerNodesRequestTest {

    @Mock
    private DiscoveryNode localNode1;

    @Mock
    private DiscoveryNode localNode2;

    private MLUndeployControllerNodeRequest undeployControllerNodeRequestWithStringNodeIds;

    private MLUndeployControllerNodeRequest undeployControllerNodeRequestWithDiscoveryNodeIds;

    @Before
    public void setUp() throws Exception {

        String modelId = "testModelId";
        String[] stringNodeIds = { "nodeId1", "nodeId2", "nodeId3" };
        DiscoveryNode[] discoveryNodeIds = { localNode1, localNode2 };

        undeployControllerNodeRequestWithStringNodeIds = new MLUndeployControllerNodeRequest(
                new MLUndeployControllerNodesRequest(stringNodeIds, modelId));
        undeployControllerNodeRequestWithDiscoveryNodeIds = new MLUndeployControllerNodeRequest(
                new MLUndeployControllerNodesRequest(discoveryNodeIds, modelId));

    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        undeployControllerNodeRequestWithStringNodeIds.writeTo(output);
        assertEquals("testModelId",
                undeployControllerNodeRequestWithStringNodeIds.getUndeployControllerNodesRequest().getModelId());

    }

    @Test
    public void testConstructorSerialization2() {
        assertEquals(2, undeployControllerNodeRequestWithDiscoveryNodeIds.getUndeployControllerNodesRequest()
                .concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        undeployControllerNodeRequestWithStringNodeIds.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUndeployControllerNodeRequest parsedNodeRequest = new MLUndeployControllerNodeRequest(streamInput);

        assertEquals(undeployControllerNodeRequestWithStringNodeIds.getUndeployControllerNodesRequest().getModelId(),
                parsedNodeRequest.getUndeployControllerNodesRequest().getModelId());

    }
}
