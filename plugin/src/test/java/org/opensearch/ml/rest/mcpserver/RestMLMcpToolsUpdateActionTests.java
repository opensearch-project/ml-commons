/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

public class RestMLMcpToolsUpdateActionTests extends OpenSearchTestCase {

    private RestMLMcpToolsUpdateAction restMLMcpToolsUpdateAction;

    private final String updateToolRequest =
        """
            {
                "tools": [
                    {
                        "name": "ListIndexTool",
                        "description": "This is my first list index tool",
                        "parameters": {},
                        "attributes": {
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "indices": {
                                        "type": "array",
                                        "items": {
                                            "type": "string"
                                        },
                                        "description": "OpenSearch index name list, separated by comma. for example: [\\"index1\\", \\"index2\\"], use empty array [] to list all indices in the cluster"
                                    }
                                },
                                "additionalProperties": false
                            }
                        }
                    }
                ]
            }
            """;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private ClusterService clusterService;

    private Map<String, Tool.Factory> toolFactories = new HashMap<>();
    private DiscoveryNode discoveryNode = mock(DiscoveryNode.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        toolFactories.put("ListIndexTool", ListIndexTool.Factory.getInstance());
        when(discoveryNode.getId()).thenReturn("mockId");
        when(clusterService.state().nodes().getNodes()).thenReturn(ImmutableMap.of("mockId", discoveryNode));
        Settings settings = Settings.builder().put(ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_SERVER_ENABLED)));
        restMLMcpToolsUpdateAction = new RestMLMcpToolsUpdateAction(clusterService);
    }

    public void test_doExecute_featureFlagDisabled() throws IOException {
        exceptionRule.expect(OpenSearchException.class);
        exceptionRule.expectMessage("The MCP server is not enabled. To enable, please update the setting plugins.ml_commons.mcp_server_enabled");
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), false).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
                .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        RestMLMcpToolsUpdateAction restMLMcpToolsUpdateAction = new RestMLMcpToolsUpdateAction(clusterService);
        BytesReference bytesReference = BytesReference
                .fromByteBuffer(ByteBuffer.wrap(updateToolRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
                .build();
        restMLMcpToolsUpdateAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_prepareRequest_successful() throws IOException {
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap(updateToolRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLMcpToolsUpdateAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_prepareRequest_toolListIsNull() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        String emptyToolListRequest = """
            {
                "tools": []
            }
            """;
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap(emptyToolListRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLMcpToolsUpdateAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_getName() {
        assertEquals("ml_mcp_tools_update_action", restMLMcpToolsUpdateAction.getName());
    }

    @Test
    public void test_routes() {
        Set<String> expectedRoutes = Set.of("POST /_plugins/_ml/mcp/tools/_update");
        Set<String> routes = restMLMcpToolsUpdateAction
            .routes()
            .stream()
            .map(r -> r.getMethod().name() + " " + r.getPath())
            .collect(Collectors.toSet());
        assertEquals(expectedRoutes, routes);
    }
}
