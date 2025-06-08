/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptGetRequest;
import org.opensearch.ml.common.transport.prompt.MLPromptGetResponse;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetPromptTransportActionTests extends OpenSearchTestCase {
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
    private ActionListener<MLPromptGetResponse> actionListener;

    @Mock
    private GetResponse getResponse;

    private GetPromptTransportAction getPromptTransportAction;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLPromptGetRequest mlPromptGetRequest;

    @Mock
    private MLPromptManager mlPromptManager;

    @Mock
    private EncryptorImpl encryptor;

    @Captor
    private ArgumentCaptor<GetDataObjectRequest> getDataObjectRequestArgumentCaptor;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        mlPromptGetRequest = MLPromptGetRequest.builder().promptId(PROMPT_ID).tenantId(TENANT_ID).build();
        when(getResponse.getId()).thenReturn(PROMPT_ID);
        when(getResponse.getSourceAsString()).thenReturn("{}");

        getPromptTransportAction = spy(
            new GetPromptTransportAction(transportService, actionFilters, client, sdkClient, encryptor, mlFeatureEnabledSetting, mlPromptManager)
        );

        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testConstructor() {
        GetPromptTransportAction getPromptTransportAction = new GetPromptTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            encryptor,
            mlFeatureEnabledSetting,
            mlPromptManager
        );
        assertNotNull(getPromptTransportAction);
    }

    @Test
    public void testDoExecute_success() throws IOException {
        MLPrompt mlPrompt = createMLPrompt();
        doAnswer(invocation -> {
            ActionListener<MLPrompt> listener = invocation.getArgument(2);
            listener.onResponse(mlPrompt);
            return null;
        }).when(mlPromptManager).getPromptAsync(getDataObjectRequestArgumentCaptor.capture(), any(), any());

        getPromptTransportAction.doExecute(task, mlPromptGetRequest, actionListener);

        assertEquals(TENANT_ID, getDataObjectRequestArgumentCaptor.getValue().tenantId());
        assertEquals(PROMPT_ID, getDataObjectRequestArgumentCaptor.getValue().id());
        ArgumentCaptor<MLPromptGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLPromptGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        MLPrompt capturedPrompt = argumentCaptor.getValue().getMlPrompt();
        assertNotNull(capturedPrompt);
        assertEquals("test prompt", capturedPrompt.getName());
        assertEquals(mlPrompt.getPrompt(), capturedPrompt.getPrompt());
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

        getPromptTransportAction.doExecute(task, mlPromptGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find prompt with the provided prompt id: prompt_id", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_multi_tenancy_fail() throws InterruptedException {
        MLPromptGetRequest mlPromptGetRequest = MLPromptGetRequest.builder().promptId(PROMPT_ID).build();
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        getPromptTransportAction.doExecute(task, mlPromptGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    private MLPrompt createMLPrompt() {
        Map<String, String> prompt = new HashMap<>();
        prompt.put("user", "test prompt");
        prompt.put("system", "test prompt");
        return MLPrompt.builder().name("test prompt").prompt(prompt).build();
    }
}
