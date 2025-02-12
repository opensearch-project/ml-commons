/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
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

public class SearchTaskTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;
    SdkClient sdkClient;

    SearchResponse searchResponse;
    @Mock
    NamedXContentRegistry namedXContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    SearchRequest searchRequest;

    @Mock
    ActionListener<SearchResponse> actionListener;

    SearchTaskTransportAction searchTaskTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        searchTaskTransportAction = new SearchTaskTransportAction(
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

    public void test_DoExecute() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(eq(mlSearchActionRequest), any());

        searchTaskTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(client, times(1)).search(eq(mlSearchActionRequest), any());
        // Use ArgumentCaptor to capture the SearchResponse
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        // Capture the response passed to actionListener.onResponse
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        // Assert that the captured response matches the expected values
        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse.getHits().getTotalHits(), capturedResponse.getHits().getTotalHits());
        assertEquals(searchResponse.getHits().getHits().length, capturedResponse.getHits().getHits().length);
        assertEquals(searchResponse.status(), capturedResponse.status());
    }
}
