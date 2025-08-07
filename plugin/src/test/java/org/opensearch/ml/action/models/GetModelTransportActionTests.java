/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetModelTransportActionTests extends OpenSearchTestCase {

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

    SdkClient sdkClient;

    @Mock
    ActionListener<MLModelGetResponse> actionListener;

    @Mock
    ClusterService clusterService;

    private Settings settings;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetModelTransportAction getModelTransportAction;
    MLModelGetRequest mlModelGetRequest;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlModelGetRequest = MLModelGetRequest.builder().modelId("test_id").build();
        settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        getModelTransportAction = spy(
            new GetModelTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testGetModel_UserHasNodeAccess() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_Success() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<MLModelGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLModelGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testGetModelHidden_Success() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(true);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(true).when(getModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<MLModelGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLModelGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testGetModelHidden_SuperUserPermissionError() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(true);
        mlModelGetRequest = MLModelGetRequest.builder().modelId("test_id").isUserInitiatedGetRequest(true).build();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(false).when(getModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_ValidateAccessFailed() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        GetResponse getResponse = prepareMLModel(false);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_NotExistsResponse() {
        GetResult getResult = new GetResult(
            ML_MODEL_INDEX,
            "fake_id",
            UNASSIGNED_SEQ_NO,
            UNASSIGNED_PRIMARY_TERM,
            -1L,
            false,
            null,
            null,
            null
        );
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(new GetResponse(getResult));
            return null;
        }).when(client).get(any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model with the provided model id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_IndexNotFoundException() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Fail to find model"));
            return null;
        }).when(client).get(any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).get(any(), any());

        getModelTransportAction.doExecute(null, mlModelGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-model", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLModel(boolean isHidden) throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .modelState(MLModelState.REGISTERED)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .isHidden(isHidden)
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
