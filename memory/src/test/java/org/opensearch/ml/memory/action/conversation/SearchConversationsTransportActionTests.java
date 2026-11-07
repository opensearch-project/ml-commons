/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.action.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;

import java.io.IOException;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.memory.MemoryTestUtil;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.suggest.SuggestBuilder;
import org.opensearch.search.suggest.SuggestBuilders;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class SearchConversationsTransportActionTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<SearchResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    @Mock
    SearchRequest request;

    MLSearchActionRequest mlSearchActionRequest;

    SearchConversationsTransportAction action;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlSearchActionRequest = new MLSearchActionRequest(request, null);

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED)));

        this.action = spy(new SearchConversationsTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
    }

    public void testEnabled_ThenSucceed() {
        SearchResponse response = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(cmHandler).searchConversations(any(), any());
        action.doExecute(null, mlSearchActionRequest, actionListener);
        ArgumentCaptor<SearchResponse> argCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().equals(response));
    }

    public void testDisabled_ThenFail() {
        clusterService = MemoryTestUtil.clusterServiceWithMemoryFeatureDisabled();
        this.action = spy(new SearchConversationsTransportAction(transportService, actionFilters, cmHandler, client, clusterService));

        action.doExecute(null, mlSearchActionRequest, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assertEquals(argCaptor.getValue().getMessage(), ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE);
    }

    public void testQueryScopedTermsAggregation_ThenSucceed() {
        SearchSourceBuilder source = new SearchSourceBuilder().aggregation(AggregationBuilders.terms("by_app").field("application_type"));
        MLSearchActionRequest request = new MLSearchActionRequest(new SearchRequest().source(source), null);
        SearchResponse response = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(cmHandler).searchConversations(any(), any());

        action.doExecute(null, request, actionListener);

        verify(cmHandler, times(1)).searchConversations(any(), any());
        verify(actionListener, times(1)).onResponse(response);
    }

    public void testGlobalAggregation_ThenFail() {
        SearchSourceBuilder source = new SearchSourceBuilder()
            .aggregation(AggregationBuilders.global("all").subAggregation(AggregationBuilders.topHits("docs").size(100)));
        MLSearchActionRequest request = new MLSearchActionRequest(new SearchRequest().source(source), null);
        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        verify(cmHandler, never()).searchConversations(any(), any());
    }

    public void testSuggest_ThenFail() {
        SearchSourceBuilder source = new SearchSourceBuilder()
            .suggest(new SuggestBuilder().addSuggestion("s", SuggestBuilders.termSuggestion("name").text("a")));
        MLSearchActionRequest request = new MLSearchActionRequest(new SearchRequest().source(source), null);
        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        verify(cmHandler, never()).searchConversations(any(), any());
    }

    public void testDisabledWithAggregation_ThenFeatureDisabledError() {
        clusterService = MemoryTestUtil.clusterServiceWithMemoryFeatureDisabled();
        this.action = spy(new SearchConversationsTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
        SearchSourceBuilder source = new SearchSourceBuilder().aggregation(AggregationBuilders.global("all"));
        MLSearchActionRequest request = new MLSearchActionRequest(new SearchRequest().source(source), null);
        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertEquals(ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE, captor.getValue().getMessage());
    }

    public void testExplain_ThenFail() {
        SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.prefixQuery("name", "").rewrite("constant_score_boolean"))
            .explain(true);
        MLSearchActionRequest request = new MLSearchActionRequest(new SearchRequest().source(source), null);
        ActionListener<SearchResponse> listener = mock(ActionListener.class);

        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) captor.getValue()).status());
        verify(cmHandler, never()).searchConversations(any(), any());
    }
}
