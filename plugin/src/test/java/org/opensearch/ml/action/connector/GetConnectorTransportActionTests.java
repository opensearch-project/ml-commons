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
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetConnectorTransportActionTests extends OpenSearchTestCase {
    private static final String CONNECTOR_ID = "connector_id";

    private static final String TENANT_ID = "_tenant_id";

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLConnectorGetResponse> actionListener;

    @Mock
    GetResponse getResponse;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    GetConnectorTransportAction getConnectorTransportAction;
    MLConnectorGetRequest mlConnectorGetRequest;
    ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Captor
    private ArgumentCaptor<GetDataObjectRequest> getDataObjectRequestArgumentCaptor;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(CONNECTOR_ID).tenantId(TENANT_ID).build();
        when(getResponse.getId()).thenReturn(CONNECTOR_ID);
        when(getResponse.getSourceAsString()).thenReturn("{}");
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        getConnectorTransportAction = spy(
            new GetConnectorTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                connectorAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testGetConnector_UserHasNoAccess() throws IOException, InterruptedException {
        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").tenantId("tenantId").build();
        when(connectorAccessControlHelper.hasPermission(any(), any())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener.onResponse(httpConnector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this connector", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetConnector_NullResponse() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener
                .onFailure(
                    new OpenSearchStatusException(
                        "Failed to find connector with the provided connector id: connector_id",
                        RestStatus.NOT_FOUND
                    )
                );
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find connector with the provided connector id: connector_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetConnector_MultiTenancyEnabled_Success() throws IOException, InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        when(connectorAccessControlHelper.hasPermission(any(), any())).thenReturn(true);
        String tenantId = "test_tenant";
        mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(CONNECTOR_ID).tenantId(tenantId).build();

        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").tenantId(tenantId).build();
        when(connectorAccessControlHelper.hasPermission(any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener.onResponse(httpConnector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(),
                getDataObjectRequestArgumentCaptor.capture(), any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);

        Assert.assertEquals(tenantId, getDataObjectRequestArgumentCaptor.getValue().tenantId());
        Assert.assertEquals(CONNECTOR_ID, getDataObjectRequestArgumentCaptor.getValue().id());
        ArgumentCaptor<MLConnectorGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLConnectorGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(tenantId, argumentCaptor.getValue().getMlConnector().getTenantId());
    }

    @Test
    public void testGetConnector_MultiTenancyEnabled_ForbiddenAccess() throws IOException, InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        when(connectorAccessControlHelper.hasPermission(any(), any())).thenReturn(true);
        String tenantId = "test_tenant";
        mlConnectorGetRequest = MLConnectorGetRequest.builder().connectorId(CONNECTOR_ID).tenantId(tenantId).build();

        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").tenantId("tenantId").build();
        when(connectorAccessControlHelper.hasPermission(any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener.onResponse(httpConnector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());

        getConnectorTransportAction.doExecute(null, mlConnectorGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareConnector(String tenantId) throws IOException {
        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").tenantId(tenantId).build();

        XContentBuilder content = httpConnector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
