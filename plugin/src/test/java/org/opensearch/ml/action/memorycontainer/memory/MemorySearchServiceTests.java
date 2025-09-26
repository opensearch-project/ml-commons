/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.ShortTermMemoryType;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.transport.client.Client;

public class MemorySearchServiceTests {

    @Mock
    private Client client;

    @Mock
    private ActionListener<List<FactSearchResult>> listener;

    MLAddMemoriesInput input;
    MemoryStrategy strategy;

    private MemorySearchService memorySearchService;

    private String sessionId;
    private MemoryConfiguration memoryConfig;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        memorySearchService = new MemorySearchService(client);
        sessionId = "session-123";
        List<MessageInput> messages = new ArrayList<>();
        messages.add(MessageInput.builder().role("user").contentText("hello, I'm bob. I like swimming").build());
        input = spy(
            MLAddMemoriesInput
                .builder()
                .namespace(Map.of(SESSION_ID_FIELD, sessionId))
                .memoryType(ShortTermMemoryType.CONVERSATION)
                .memoryContainerId("container-123")
                .infer(true)
                .messages(messages)
                .build()
        );
        strategy = spy(MemoryStrategy.builder().id("strategy-123").type("semantic").namespace(List.of(SESSION_ID_FIELD)).build());

        memoryConfig = spy(
            MemoryConfiguration
                .builder()
                .llmId("llm-id")
                .embeddingModelId("embedding-model-id")
                .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                .dimension(512)
                .maxInferSize(5)
                .strategies(List.of(strategy))
                .build()
        );
    }

    @Test
    public void testSearchSimilarFactsForSession_EmptyFacts() {
        List<String> facts = Arrays.asList();
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_NullSessionId() {
        List<String> facts = Arrays.asList("User name is John");
        String sessionId = null;
        String indexName = "memory-index";
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        when(input.getNamespace()).thenReturn(Map.of());
        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_SearchFailure() {
        List<String> facts = Arrays.asList("User name is John");
        Exception searchException = new RuntimeException("Search failed");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onFailure(searchException);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, memoryConfig, listener);

        verify(client).search(any(), any());
    }

    @Test
    public void testSearchSimilarFactsForSession_WithMaxInferSizeLimit() throws IOException {
        List<String> facts = Arrays.asList("Fact1", "Fact2", "Fact3");

        SearchResponse searchResponse = mock(SearchResponse.class);
        XContentBuilder sourceContent = XContentBuilder
            .builder(XContentType.JSON.xContent())
            .startObject()
            .field(MEMORY_FIELD, "test memory")
            .endObject();
        SearchHit h1 = new SearchHit(1);
        h1.sourceRef(BytesReference.bytes(sourceContent));

        SearchHits hits = new SearchHits(new SearchHit[] { h1 }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(searchResponse.getHits()).thenReturn(hits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, memoryConfig, listener);

        verify(client, times(3)).search(any(), any()); // Limited by maxInferSize
        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_EmptySessionId() {
        List<String> facts = Arrays.asList("Test fact");
        when(input.getNamespace()).thenReturn(Map.of());
        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, memoryConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

    @Test
    public void testSearchSimilarFactsForSession_EmptyFactsList() {
        List<String> facts = Arrays.asList();
        MemoryConfiguration storageConfig = mock(MemoryConfiguration.class);

        memorySearchService.searchSimilarFactsForSession(strategy, input, facts, storageConfig, listener);

        verify(listener).onResponse(any(List.class));
    }

}
