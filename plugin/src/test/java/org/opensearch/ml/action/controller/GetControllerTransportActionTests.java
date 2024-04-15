/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.MockHelper.mock_client_get_NotExist;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
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
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLController;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.transport.controller.MLControllerGetRequest;
import org.opensearch.ml.common.transport.controller.MLControllerGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetControllerTransportActionTests extends OpenSearchTestCase {
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
    ActionListener<MLControllerGetResponse> actionListener;

    @Mock
    ClusterService clusterService;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    MLModelManager mlModelManager;

    @Mock
    MLModel mlModel;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetControllerTransportAction getControllerTransportAction;
    MLControllerGetRequest mlControllerGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        getControllerTransportAction = spy(
            new GetControllerTransportAction(
                transportService,
                actionFilters,
                client,
                xContentRegistry,
                clusterService,
                mlModelManager,
                modelAccessControlHelper
            )
        );
        mlControllerGetRequest = MLControllerGetRequest.builder().modelId("testModelId").build();

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        GetResponse getResponse = prepareControllerGetResponse();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testGetControllerSuccess() {
        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        verify(actionListener).onResponse(any(MLControllerGetResponse.class));
    }

    @Test
    public void testGetControllerWithModelAccessControlNoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User doesn't have privilege to perform this operation on this model controller. Model ID: testModelId",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testGetControllerWithModelAccessControlOtherException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetControllerWithGetModelNotFound() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find model to get the corresponding model controller with the provided model ID",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testGetControllerWithGetModelOtherException() {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(mlModelManager).getModel(eq("testModelId"), any(), any(), isA(ActionListener.class));

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Failed to find model to get the corresponding model controller with the provided model ID",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testGetControllerOtherException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Exception occurred. Please check log for more details."));
            return null;
        }).when(client).get(any(), any());

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Exception occurred. Please check log for more details.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetControllerNotFound() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model controller with the provided model ID", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetControllerClientFailedToGetThreadPool() {
        mock_client_get_NotExist(client);
        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model controller with the provided model ID", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetControllerIndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Failed to find model controller"));
            return null;
        }).when(client).get(any(), any());

        getControllerTransportAction.doExecute(null, mlControllerGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model controller", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareControllerGetResponse() throws IOException {

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();

        MLController controller = MLController.builder().modelId("testModelId").userRateLimiter(new HashMap<>() {
            {
                put("testUser", rateLimiter);
            }
        }).build();

        XContentBuilder content = controller.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
