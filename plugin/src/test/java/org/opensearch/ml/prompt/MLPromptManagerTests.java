/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.prompt.MLPrompt;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLPromptManagerTests extends OpenSearchTestCase {
    private static final String PROMPT_ID = "prompt_id";

    @Mock
    private Client client;
    private SdkClient sdkClient;

    @Mock
    ThreadPool threadPool;

    ThreadContext threadContext;

    @Mock
    ActionListener<MLPrompt> getPromptActionListener;

    private MLPromptManager mlPromptManager;

    @Mock
    private GetResponse getResponse;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        mlPromptManager = spy(new MLPromptManager(client, sdkClient));

        getResponse = createGetResponse();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
    }

    @Test
    public void testConstructor() {
        MLPromptManager mlPromptManager = new MLPromptManager(client, sdkClient);
        assertNotNull(mlPromptManager);
    }

    @Test
    public void testGetPrompt_success() throws IOException {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(PROMPT_ID).build();
        mlPromptManager
            .getPromptAsync(
                getDataObjectRequest,
                PROMPT_ID,
                getPromptActionListener

            );

        ArgumentCaptor<MLPrompt> argumentCaptor = ArgumentCaptor.forClass(MLPrompt.class);
        verify(getPromptActionListener).onResponse(argumentCaptor.capture());

        MLPrompt capturedPrompt = argumentCaptor.getValue();
        assertNotNull(capturedPrompt);
        assertEquals("test prompt", capturedPrompt.getName());
    }

    @Test
    public void testGetPromptException() {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(PROMPT_ID).build();

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get prompt"));
            return null;
        }).when(client).get(any(), any());

        mlPromptManager.getPromptAsync(getDataObjectRequest, PROMPT_ID, getPromptActionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getPromptActionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-prompt", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetPromptIndexNotFound() throws IOException {
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest.builder().index(ML_PROMPT_INDEX).id(PROMPT_ID).build();

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index not found"));
            return null;
        }).when(client).get(any(), any());

        mlPromptManager.getPromptAsync(getDataObjectRequest, PROMPT_ID, getPromptActionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(getPromptActionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find prompt with the provided prompt id: prompt_id", argumentCaptor.getValue().getMessage());
        assertEquals(RestStatus.NOT_FOUND, argumentCaptor.getValue().status());
    }

    private GetResponse createGetResponse() {
        Map<String, String> prompt = new HashMap<>();
        prompt.put("user", "test prompt");
        prompt.put("system", "test prompt");
        MLPrompt mlPrompt = MLPrompt.builder().name("test prompt").prompt(prompt).build();
        XContentBuilder content;
        try {
            content = mlPrompt.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(ML_PROMPT_INDEX, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
