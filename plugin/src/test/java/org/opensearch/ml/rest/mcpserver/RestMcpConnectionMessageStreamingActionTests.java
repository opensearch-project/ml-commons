/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableMap;

public class RestMcpConnectionMessageStreamingActionTests extends OpenSearchTestCase {

    private RestMcpConnectionMessageStreamingAction restMcpConnectionMessageStreamingAction;

    StreamingRestChannel channel = mock(StreamingRestChannel.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private NodeClient client;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ClusterService clusterService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        DiscoveryNode localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.state().nodes().getNodes()).thenReturn(ImmutableMap.of("foo0", localNode));
        when(clusterService.localNode()).thenReturn(localNode);
        Settings settings = Settings.builder().put(ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_SERVER_ENABLED)));
        restMcpConnectionMessageStreamingAction = new RestMcpConnectionMessageStreamingAction(clusterService);
        doAnswer(invocationOnMock -> {
            ActionListener<IndexResponse> listener = invocationOnMock.getArgument(1);
            IndexResponse response = mock(IndexResponse.class);
            when(response.getId()).thenReturn("foo0");
            when(response.status()).thenReturn(RestStatus.CREATED);
            listener.onResponse(response);
            return null;
        }).when(client).index(any(), isA(ActionListener.class));
    }

    @Test
    public void test_prepareConnectionRequest_withRestRequest_successful() {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath(RestMcpConnectionMessageStreamingAction.SSE_ENDPOINT)
            .build();
        restMcpConnectionMessageStreamingAction.prepareRequest(request, mock(NodeClient.class));
    }

    @Test
    public void test_prepareConnectionRequest_successful() {
        restMcpConnectionMessageStreamingAction
            .prepareRequestInternal(RestMcpConnectionMessageStreamingAction.SSE_ENDPOINT, true, null, channel, client);
        verify(channel, times(1)).prepareResponse(any(), any());
    }

    @Test
    public void test_prepareMessageRequest_successful() {
        restMcpConnectionMessageStreamingAction
            .prepareRequestInternal(
                RestMcpConnectionMessageStreamingAction.MESSAGE_ENDPOINT,
                true,
                UUID.randomUUID().toString(),
                channel,
                client
            );
        verify(channel, times(1)).prepareResponse(any(), any());
    }

    @Test
    public void test_prepareMessageRequest_sessionIdIsNull() {
        try {
            restMcpConnectionMessageStreamingAction
                .prepareRequestInternal(RestMcpConnectionMessageStreamingAction.MESSAGE_ENDPOINT, true, null, channel, client);
        } catch (Exception e) {
            // The NPE is caused by not mocking the request, ignore it.
        }
        verify(channel, times(1)).prepareResponse(any(), any());
    }

}
