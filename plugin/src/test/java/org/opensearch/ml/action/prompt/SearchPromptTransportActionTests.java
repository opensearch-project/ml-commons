/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class SearchPromptTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;
    SdkClient sdkClient;

    SearchResponse searchResponse;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    ActionListener<SearchResponse> actionListener;

    SearchPromptTransportAction searchPromptTransportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        searchPromptTransportAction = new SearchPromptTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting
        );
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0L, TotalHits.Relation.EQUAL_TO), Float.NaN);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            null,
            false,
            null,
            0
        );
        searchResponse = new SearchResponse(
            internalSearchResponse,
            null,
            0,
            0,
            0,
            1,
            ShardSearchFailure.EMPTY_ARRAY,
            mock(SearchResponse.Clusters.class),
            null
        );
    }

    public void testConstructor() {
        SearchPromptTransportAction searchPromptTransportAction = new SearchPromptTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting
        );
        assertNotNull(searchPromptTransportAction);
    }

    public void testDoExecute_success() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        searchPromptTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse.getHits().getTotalHits(), capturedResponse.getHits().getTotalHits());
        assertEquals(searchResponse.getHits().getHits().length, capturedResponse.getHits().getHits().length);
        assertEquals(searchResponse.status(), capturedResponse.status());
    }

    public void testDoExecute_fail() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchACtionRequest = new MLSearchActionRequest(request, null);

        when(client.threadPool()).thenReturn(null);

        searchPromptTransportAction.doExecute(null, mlSearchACtionRequest, actionListener);
        verify(actionListener).onFailure(any(NullPointerException.class));
    }

    public void testDoExecute_multi_tenancy_fail() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchACtionRequest = new MLSearchActionRequest(request, null);

        searchPromptTransportAction.doExecute(null, mlSearchACtionRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You don't have permission to access this resource",
                argumentCaptor.getValue().getMessage()
        );
    }
}
