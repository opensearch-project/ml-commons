/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptRequest;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class UpdatePromptTransportActionTests extends OpenSearchTestCase {
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
    private ActionListener<UpdateResponse> actionListener;

    private UpdatePromptTransportAction updatePromptTransportAction;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLUpdatePromptRequest mlUpdatePromptRequest;

    private MLUpdatePromptInput mlUpdatePromptInput;

    @Mock
    private MLPromptManager mlPromptManager;

    UpdateResponse updateResponse;

    @Captor
    private ArgumentCaptor<GetDataObjectRequest> getDataObjectRequestArgumentCaptor;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        updatePromptTransportAction = spy(
            new UpdatePromptTransportAction(transportService, actionFilters, client, sdkClient, mlFeatureEnabledSetting, mlPromptManager)
        );
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
        Map<String, String> testPrompt = new HashMap<>();
        testPrompt.put("system", "test updated system prompt");
        testPrompt.put("user", "test updated user prompt");
        mlUpdatePromptInput = MLUpdatePromptInput.builder().name("test_prompt").prompt(testPrompt).build();
        mlUpdatePromptRequest = MLUpdatePromptRequest.builder().promptId("prompt_id").mlUpdatePromptInput(mlUpdatePromptInput).build();
    }

    @Test
    public void testConstructor() {
        UpdatePromptTransportAction updatePromptTransportAction = new UpdatePromptTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting,
            mlPromptManager
        );
        assertNotNull(updatePromptTransportAction);
    }

    @Test
    public void testDoExecute_success() throws IOException {
        MLPrompt mlPrompt = createMLPrompt();
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener.onResponse(mlPrompt);
            return null;
        }).when(mlPromptManager).getPromptAsync(getDataObjectRequestArgumentCaptor.capture(), any(), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updatePromptTransportAction.doExecute(task, mlUpdatePromptRequest, actionListener);

        ArgumentCaptor<UpdateResponse> argumentCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(updateResponse.getId(), argumentCaptor.getValue().getId());
        assertEquals(updateResponse.getResult(), argumentCaptor.getValue().getResult());
    }

    @Test
    public void testDoExecute_multi_tenancy_fail() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        updatePromptTransportAction.doExecute(task, mlUpdatePromptRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_fail_withIndexNotFoundException() {
        MLPrompt mlPrompt = createMLPrompt();
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener.onResponse(mlPrompt);
            return null;
        }).when(mlPromptManager).getPromptAsync(getDataObjectRequestArgumentCaptor.capture(), any(), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index not found"));
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updatePromptTransportAction.doExecute(task, mlUpdatePromptRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update data object in index .plugins-ml-prompt", argumentCaptor.getValue().getMessage());
    }

    private MLPrompt createMLPrompt() {
        Map<String, String> prompt = new HashMap<>();
        prompt.put("user", "test prompt");
        prompt.put("system", "test prompt");
        return MLPrompt.builder().name("test prompt").prompt(prompt).version("1").build();
    }
}
