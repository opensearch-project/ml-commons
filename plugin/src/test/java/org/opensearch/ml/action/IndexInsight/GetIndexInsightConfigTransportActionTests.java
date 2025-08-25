/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetIndexInsightConfigTransportActionTests {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionListener<MLIndexInsightConfigGetResponse> actionListener;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private CompletionStage<GetDataObjectResponse> responseCompletionStage;

    @Mock
    private GetDataObjectResponse getDataObjectResponse;

    @Mock
    private Throwable throwable;

    GetIndexInsightConfigTransportAction getIndexInsightConfigTransportAction;
    MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightConfigGetRequest = MLIndexInsightConfigGetRequest.builder().tenantId(null).build();

        getIndexInsightConfigTransportAction = spy(
            new GetIndexInsightConfigTransportAction(
                transportService,
                actionFilters,
                xContentRegistry,
                mlFeatureEnabledSetting,
                client,
                sdkClient,
                mlIndicesHandler,
                clusterService
            )
        );

        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testGetIndexInsightConfig_Successful() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":true}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        getIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigGetRequest, actionListener);
        ArgumentCaptor<MLIndexInsightConfigGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightConfigGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getIndexInsightConfig().getIsEnable(), true);
    }

    @Test
    public void testGetIndexInsightConfig_FailDueToNotEnabled() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":false}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        getIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getMessage(), "Failed to get index insight config");
    }

    @Test
    public void testGetIndexInsightConfig_FailDueToGetObject() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":false}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("fail to get object"));

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        getIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getMessage(), "fail to get object");
    }

    @Test
    public void testGetIndexInsightConfig_FailDueToGetResponse() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":false}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenThrow(new RuntimeException("Fail to get response"));

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        getIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getMessage(), "Fail to get response");
    }

    @Test
    public void testGetIndexInsightConfig_FailDueToGetContext() {
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("Fail to get context"));
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":false}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenThrow(new RuntimeException("Fail to get response"));

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        getIndexInsightConfigTransportAction.doExecute(null, mlIndexInsightConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(argumentCaptor.getValue().getMessage(), "Fail to get context");
    }

}
