/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableMap;

public class RestMLMcpToolsRemoveActionTests extends OpenSearchTestCase {

    private RestMLMcpToolsRemoveAction restMLRemoveMcpToolsAction;

    private final String removeToolRequest = "[\"ListIndexTool\"]";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ClusterService clusterService;

    private DiscoveryNode discoveryNode = mock(DiscoveryNode.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        when(discoveryNode.getId()).thenReturn("mockId");
        when(clusterService.state().nodes().getNodes()).thenReturn(ImmutableMap.of("mockId", discoveryNode));
        Settings settings = Settings.builder().put(ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_SERVER_ENABLED)));
        restMLRemoveMcpToolsAction = new RestMLMcpToolsRemoveAction(clusterService);
    }

    @Test
    public void test_prepareRequest_successful() throws IOException {
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(removeToolRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLRemoveMcpToolsAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_prepareRequest_toolListIsNull() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        String emptyToolListRequest = "[]";
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap(emptyToolListRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLRemoveMcpToolsAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_getName() {
        assertEquals("ml_remove_mcp_tools_action", restMLRemoveMcpToolsAction.getName());
    }

    @Test
    public void test_routes() {
        Set<String> expectedRoutes = Set.of("POST /_plugins/_ml/mcp/tools/_remove");
        Set<String> routes = restMLRemoveMcpToolsAction
            .routes()
            .stream()
            .map(r -> r.getMethod().name() + " " + r.getPath())
            .collect(Collectors.toSet());
        assertEquals(expectedRoutes, routes);
    }

}
