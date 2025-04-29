/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestMcpConnectionMessageStreamingActionTests extends OpenSearchTestCase {

    private RestMcpConnectionMessageStreamingAction restMcpConnectionMessageStreamingAction;

    StreamingRestChannel channel = mock(StreamingRestChannel.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        restMcpConnectionMessageStreamingAction = new RestMcpConnectionMessageStreamingAction();
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
        restMcpConnectionMessageStreamingAction.prepareRequestInternal(RestMcpConnectionMessageStreamingAction.SSE_ENDPOINT, null, channel);
        verify(channel, times(1)).prepareResponse(any(), any());
    }

    @Test
    public void test_prepareMessageRequest_successful() {
        restMcpConnectionMessageStreamingAction
            .prepareRequestInternal(RestMcpConnectionMessageStreamingAction.MESSAGE_ENDPOINT, UUID.randomUUID().toString(), channel);
        verify(channel, times(1)).prepareResponse(any(), any());
    }

    @Test
    public void test_prepareMessageRequest_sessionIdIsNull() {
        try {
            restMcpConnectionMessageStreamingAction
                .prepareRequestInternal(RestMcpConnectionMessageStreamingAction.MESSAGE_ENDPOINT, null, channel);
        } catch (Exception e) {
            // The NPE is caused by not mocking the request, ignore it.
        }
        verify(channel, times(1)).prepareResponse(any(), any());
    }

}
