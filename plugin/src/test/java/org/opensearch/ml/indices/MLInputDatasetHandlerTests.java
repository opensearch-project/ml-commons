/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.indices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class MLInputDatasetHandlerTests extends OpenSearchTestCase {
    Client client;
    MLInputDatasetHandler mlInputDatasetHandler;
    ActionListener<MLInputDataset> listener;
    DataFrame dataFrame;
    SearchResponse searchResponse;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        Map<String, Object> source = new HashMap<>();
        source.put("taskId", "111");
        List<Map<String, Object>> mapList = new ArrayList<>();
        mapList.add(source);
        dataFrame = DataFrameBuilder.load(mapList);
        client = mock(Client.class);
        mlInputDatasetHandler = new MLInputDatasetHandler(client);
        listener = spy(new ActionListener<MLInputDataset>() {
            @Override
            public void onResponse(MLInputDataset inputDataset) {}

            @Override
            public void onFailure(Exception e) {}
        });

    }

    public void testDataFrameInputDataset() {
        DataFrame testDataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder().dataFrame(testDataFrame).build();
        DataFrame result = mlInputDatasetHandler.parseDataFrameInput(dataFrameInputDataset);
        Assert.assertEquals(testDataFrame, result);
    }

    public void testDataFrameInputDatasetWrongType() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Input dataset is not DATA_FRAME type.");
        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Collections.singletonList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            .build();
        mlInputDatasetHandler.parseDataFrameInput(searchQueryInputDataset);
    }

    @SuppressWarnings("unchecked")
    public void testSearchQueryInputDatasetWithHits() {
        searchResponse = mock(SearchResponse.class);
        BytesReference bytesArray = new BytesArray("{\"taskId\":\"111\"}");
        SearchHit hit = new SearchHit(1);
        hit.sourceRef(bytesArray);
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1L, TotalHits.Relation.EQUAL_TO), 1f);
        when(searchResponse.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments()[1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Collections.singletonList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        ArgumentCaptor<MLInputDataset> captor = ArgumentCaptor.forClass(MLInputDataset.class);
        verify(listener, times(1)).onResponse(captor.capture());
        Assert.assertEquals(captor.getAllValues().size(), 1);
    }

    @SuppressWarnings("unchecked")
    public void testSearchQueryInputDatasetWithoutHits() {
        searchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1L, TotalHits.Relation.EQUAL_TO), 1f);
        when(searchResponse.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments()[1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Collections.singletonList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        verify(listener, times(1)).onFailure(any());
    }

    public void testSearchQueryInputDatasetWithNullHits() {
        searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments()[1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Collections.singletonList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        verify(listener, times(1)).onFailure(any());
    }

    public void testSearchQueryInputDatasetWithNullResponse() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments()[1];
            listener.onResponse(null);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset
            .builder()
            .indices(Collections.singletonList("index1"))
            .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
            .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        verify(listener, times(1)).onFailure(any());
    }

    public void testSearchQueryInputDatasetWrongType() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Input dataset is not SEARCH_QUERY type.");
        DataFrame testDataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder().dataFrame(testDataFrame).build();
        mlInputDatasetHandler.parseSearchQueryInput(dataFrameInputDataset, listener);
    }

}
