/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptDeleteRequest;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeletePromptTransportActionTests extends OpenSearchTestCase {
    private static final String PROMPT_ID = "prompt_id";
    private static final String TENANT_ID = "tenant_id";

    @Mock
    private TransportService transportService;

    @Mock
    private Client client;

    private SdkClient sdkClient;

    @Mock
    ThreadPool threadPool;

    ThreadContext threadContext;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Task task;

    @Mock
    private ActionListener<DeleteResponse> actionListener;

    @Mock
    private DeleteResponse deleteResponse;

    private DeletePromptTransportAction deletePromptTransportAction;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLPromptDeleteRequest mlPromptDeleteRequest;

    @Mock
    private MLPromptManager mlPromptManager;

    @Captor
    private ArgumentCaptor<GetDataObjectRequest> getDataObjectRequestArgumentCaptor;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        mlPromptDeleteRequest = MLPromptDeleteRequest.builder().promptId(PROMPT_ID).tenantId(TENANT_ID).build();
        ShardId shardId = new ShardId(new Index(ML_PROMPT_INDEX, "_na_"), 1);
        deleteResponse = new DeleteResponse(shardId, "taskId", 1, 1, 1, true);

        deletePromptTransportAction = spy(
            new DeletePromptTransportAction(transportService, actionFilters, client, sdkClient, mlFeatureEnabledSetting, mlPromptManager)
        );

        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testConstructor() {
        DeletePromptTransportAction deletePromptTransportAction = new DeletePromptTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting,
            mlPromptManager
        );
        assertNotNull(deletePromptTransportAction);
    }

    @Test
    public void testDoExecute_success() throws IOException {
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener.onResponse(MLPrompt.builder().build());
            return null;
        }).when(mlPromptManager).getPromptAsync(getDataObjectRequestArgumentCaptor.capture(), any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deletePromptTransportAction.doExecute(task, mlPromptDeleteRequest, actionListener);

        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    @Test
    public void testDoExecute_getPrompt_fail() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener
                .onFailure(
                    new OpenSearchStatusException("Failed to find prompt with the provided prompt id: prompt_id", RestStatus.NOT_FOUND)
                );
            return null;
        }).when(mlPromptManager).getPromptAsync(any(), any(), any());

        deletePromptTransportAction.doExecute(task, mlPromptDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find prompt with the provided prompt id: prompt_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_multi_tenancy_fail() throws InterruptedException {
        MLPromptDeleteRequest mlPromptGetRequest = MLPromptDeleteRequest.builder().promptId(PROMPT_ID).build();
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        deletePromptTransportAction.doExecute(task, mlPromptGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_failWithDelete() {
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener.onResponse(MLPrompt.builder().build());
            return null;
        }).when(mlPromptManager).getPromptAsync(getDataObjectRequestArgumentCaptor.capture(), any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index not found"));
            return null;
        }).when(client).delete(any(DeleteRequest.class), isA(ActionListener.class));

        deletePromptTransportAction.doExecute(task, mlPromptDeleteRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete data object from index .plugins-ml-prompt", argumentCaptor.getValue().getMessage());
    }
}
