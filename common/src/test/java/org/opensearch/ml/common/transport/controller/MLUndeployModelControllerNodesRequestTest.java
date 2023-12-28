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

// This test combined MLUndeployModelControllerNodesRequestTest and MLUndeployModelControllerNodeRequestTest together.
@RunWith(MockitoJUnitRunner.class)
public class MLUndeployModelControllerNodesRequestTest {

    @Mock
    private DiscoveryNode localNode1;

    @Mock
    private DiscoveryNode localNode2;

    private MLUndeployModelControllerNodeRequest undeployModelControllerNodeRequestWithStringNodeIds;

    private MLUndeployModelControllerNodeRequest undeployModelControllerNodeRequestWithDiscoveryNodeIds;

    @Before
    public void setUp() throws Exception {

        String modelId = "testModelId";
        String[] stringNodeIds = {"nodeId1", "nodeId2", "nodeId3"};
        DiscoveryNode[] discoveryNodeIds = {localNode1, localNode2};

        undeployModelControllerNodeRequestWithStringNodeIds = new MLUndeployModelControllerNodeRequest(
                new MLUndeployModelControllerNodesRequest(stringNodeIds, modelId)
        );
        undeployModelControllerNodeRequestWithDiscoveryNodeIds = new MLUndeployModelControllerNodeRequest(
                new MLUndeployModelControllerNodesRequest(discoveryNodeIds, modelId)
        );

    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        undeployModelControllerNodeRequestWithStringNodeIds.writeTo(output);
        assertEquals("testModelId", undeployModelControllerNodeRequestWithStringNodeIds.getUndeployModelControllerNodesRequest().getModelId());

    }

    @Test
    public void testConstructorSerialization2() {
        assertEquals(2, undeployModelControllerNodeRequestWithDiscoveryNodeIds.getUndeployModelControllerNodesRequest().concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        undeployModelControllerNodeRequestWithStringNodeIds.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUndeployModelControllerNodeRequest parsedNodeRequest = new MLUndeployModelControllerNodeRequest(streamInput);

        assertEquals(undeployModelControllerNodeRequestWithStringNodeIds.getUndeployModelControllerNodesRequest().getModelId(),
                parsedNodeRequest.getUndeployModelControllerNodesRequest().getModelId());

    }
}
