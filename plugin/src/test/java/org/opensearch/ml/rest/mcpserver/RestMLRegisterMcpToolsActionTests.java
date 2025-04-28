/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.ListIndexTool;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableMap;

public class RestMLRegisterMcpToolsActionTests extends OpenSearchTestCase {

    private RestMLRegisterMcpToolsAction restMLRegisterMcpToolsAction;

    private final String registerToolRequest =
        """
            {
                "tools": [
                    {
                        "name": "ListIndexTool",
                        "description": "This is my first list index tool",
                        "params": {},
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
        restMLRegisterMcpToolsAction = new RestMLRegisterMcpToolsAction(toolFactories, clusterService);
    }

    @Test
    public void test_prepareRequest_successful() throws IOException {
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap(registerToolRequest.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLRegisterMcpToolsAction.prepareRequest(restRequest, mock(NodeClient.class));
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
        restMLRegisterMcpToolsAction.prepareRequest(restRequest, mock(NodeClient.class));
    }

    @Test
    public void test_prepareRequest_hasUnrecognizedTool() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        String unrecognizedTool =
            """
                {
                    "tools": [
                        {
                            "name": "PPLTool",
                            "description": "Use this tool to transfer natural language to generate PPL and execute PPL to query inside. Use this tool after you know the index name, otherwise, call IndexRoutingTool first. The input parameters are: {index:IndexName, question:UserQuestion}",
                            "params": {
                                "model_type": "FINETUNE",
                                "model_id": "${your_model_id}"
                            },
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "parameters": {
                                        "type": "object",
                                        "question": {
                                            "type": "string"
                                        },
                                        "index": {
                                            "type": "string"
                                        }
                                    }
                                },
                                "required": [
                                    "question",
                                    "index"
                                ]
                            }
                        }
                    ]
                }
                """;
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(unrecognizedTool.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        restMLRegisterMcpToolsAction.prepareRequest(restRequest, mock(NodeClient.class));
    }
}
