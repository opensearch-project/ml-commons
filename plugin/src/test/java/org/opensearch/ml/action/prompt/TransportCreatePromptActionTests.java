/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.prompt.MLPromptManager.TAG_RESTRICTION_ERR_MESSAGE;
import static org.opensearch.ml.prompt.MLPromptManager.UNIQUE_NAME_ERR_MESSAGE;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.prompt.MLPromptManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportCreatePromptActionTests extends OpenSearchTestCase {

    private static final String PROMPT_ID = "prompt_id";

    @Mock
    private TransportService transportService;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

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
    private ActionListener<MLCreatePromptResponse> actionListener;

    @Mock
    private IndexResponse indexResponse;

    private TransportCreatePromptAction transportCreatePromptAction;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLCreatePromptRequest mlCreatePromptRequest;

    private MLCreatePromptInput mlCreatePromptInput;

    @Mock
    private MLPromptManager mlPromptManager;

    @Mock
    private MLEngine mlEngine;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        indexResponse = new IndexResponse(new ShardId(ML_PROMPT_INDEX, "_na_", 0), PROMPT_ID, 1, 0, 2, true);
        transportCreatePromptAction = spy(
            new TransportCreatePromptAction(
                transportService,
                actionFilters,
                mlIndicesHandler,
                client,
                sdkClient,
                mlEngine,
                mlPromptManager,
                mlFeatureEnabledSetting
            )
        );
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        Map<String, String> testPrompt = new HashMap<>();
        testPrompt.put("system", "test system prompt");
        testPrompt.put("user", "test user prompt");
        List<String> testTags = List.of("test_tag");
        mlCreatePromptInput = MLCreatePromptInput
            .builder()
            .name("test_prompt")
            .description("test")
            .version("1")
            .prompt(testPrompt)
            .tags(testTags)
            .build();
        mlCreatePromptRequest = MLCreatePromptRequest.builder().mlCreatePromptInput(mlCreatePromptInput).build();

        SearchResponse searchResponse = createSearchResponse(0);
        when(mlPromptManager.searchPromptByName(any(), any())).thenReturn(searchResponse);
    }

    @Test
    public void testConstructor() {
        TransportCreatePromptAction transportCreatePromptAction = new TransportCreatePromptAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            sdkClient,
            mlEngine,
            mlPromptManager,
            mlFeatureEnabledSetting
        );
        assertNotNull(transportCreatePromptAction);
    }

    @Test
    public void testDoExecute_success() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLPromptIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<MLCreatePromptResponse> captor = ArgumentCaptor.forClass(MLCreatePromptResponse.class);
        verify(actionListener).onResponse(captor.capture());

        MLCreatePromptResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(PROMPT_ID, response.getPromptId());
    }

    @Test
    public void testDoExecute_initIndex_fail() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLPromptIndex(isA(ActionListener.class));

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("No response to create ML Prompt Index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_multi_tenancy_fail() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You don't have permission to access this resource",
                argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testDoExecute_tags_size_restriction_fail() {
        List<String> tags = Collections.nCopies(MLPromptManager.MAX_NUMBER_OF_TAGS + 1, "tag");
        mlCreatePromptRequest.getMlCreatePromptInput().setTags(tags);

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(TAG_RESTRICTION_ERR_MESSAGE, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_tag_length_restriction_fail() {
        List<String> tags = Collections.singletonList("a".repeat(MLPromptManager.MAX_LENGTH_OF_TAG + 1));
        mlCreatePromptRequest.getMlCreatePromptInput().setTags(tags);

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(TAG_RESTRICTION_ERR_MESSAGE, argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_fail_withIndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLPromptIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index not found"));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find prompt with the provided prompt id: null", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_fail_withPromptNameAlreadyExists() throws IOException {
        SearchResponse searchResponse = createSearchResponse(1);
        when(mlPromptManager.searchPromptByName(any(), any())).thenReturn(searchResponse);

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The name you provided is already being used by another Prompt with ID: prompt_id . The conflicting name you provided: test_prompt",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void testDoExecute_fail_searchResponseParsing() throws IOException {
        SearchResponse searchResponse = createSearchResponse(0);
        when(mlPromptManager.searchPromptByName(any(), any()))
            .thenThrow(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));

        transportCreatePromptAction.doExecute(task, mlCreatePromptRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to parse search response", argumentCaptor.getValue().getMessage());
    }

    private SearchResponse createSearchResponse(long totalHits) throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        String promptContent = "{\n"
            + "                    \"_id\": \"prompt_id\",\n"
            + "                    \"name\": \"Test Prompt\",\n"
            + "                    \"description\": \"This is an example description\",\n"
            + "                    \"version\": 1,\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"last_updated_time\": 1684981986069\n"
            + "                }";
        SearchHit prompt = SearchHit.fromXContent(TestHelper.parser(promptContent));
        SearchHits hits = new SearchHits(new SearchHit[] { prompt }, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }
}
