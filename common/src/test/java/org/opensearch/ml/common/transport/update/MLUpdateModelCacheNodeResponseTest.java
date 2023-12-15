/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.transport.TransportAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class MLUpdateModelCacheNodeResponseTest {

    @Mock
    private DiscoveryNode localNode;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        localNode = new DiscoveryNode(
                "foo0",
                "foo0",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        Map<String, String> updateModelCacheStatus = new HashMap<>();
        updateModelCacheStatus.put("modelName:version", "response");
        MLUpdateModelCacheNodeResponse response = new MLUpdateModelCacheNodeResponse(localNode, updateModelCacheStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUpdateModelCacheNodeResponse newResponse = new MLUpdateModelCacheNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testSerializationDeserializationNullModelUpdateModelCacheStatus() throws IOException {
        MLUpdateModelCacheNodeResponse response = new MLUpdateModelCacheNodeResponse(localNode, null);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUpdateModelCacheNodeResponse newResponse = new MLUpdateModelCacheNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testReadProfile() throws IOException {
        MLUpdateModelCacheNodeResponse response = new MLUpdateModelCacheNodeResponse(localNode, new HashMap<>());
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUpdateModelCacheNodeResponse newResponse = MLUpdateModelCacheNodeResponse.readStats(output.bytes().streamInput());
        assertNotEquals(newResponse, response);
    }
}
