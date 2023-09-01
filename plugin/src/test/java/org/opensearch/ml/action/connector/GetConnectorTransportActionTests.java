/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetConnectorTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLConnectorGetResponse> actionListener;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    GetConnectorTransportAction getConnectorTransportAction;
    MLConnectorGetRequest mlConnectorGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId("connector_id").build();
        Settings settings = Settings.builder().build();

        getConnectorTransportAction = spy(
            new GetConnectorTransportAction(transportService, actionFilters, client, xContentRegistry, connectorAccessControlHelper)
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testGetConnector_UserHasNodeAccess() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        GetResponse getResponse = prepareConnector();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this connector", argumentCaptor.getValue().getMessage());
    }

    public void testGetConnector_ValidateAccessFailed() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), any(), any());

        GetResponse getResponse = prepareConnector();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this connector", argumentCaptor.getValue().getMessage());
    }

    public void testGetConnector_NullResponse() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find connector with the provided connector id: connector_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetConnector_IndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Fail to find model"));
            return null;
        }).when(client).get(any(), any());
        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find connector", argumentCaptor.getValue().getMessage());
    }

    public void testGetConnector_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).get(any(), any());
        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareConnector() throws IOException {
        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").build();

        XContentBuilder content = httpConnector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
