/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLBatchTaskUpdateProcessorTests {

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    private MLBatchTaskUpdateProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        MLBatchTaskUpdateProcessor.reset();
        processor = MLBatchTaskUpdateProcessor.getInstance(clusterService, client, threadPool);
    }

    @Test
    public void testGetInstance() {
        MLBatchTaskUpdateProcessor instance1 = MLBatchTaskUpdateProcessor.getInstance(clusterService, client, threadPool);
        MLBatchTaskUpdateProcessor instance2 = MLBatchTaskUpdateProcessor.getInstance(clusterService, client, threadPool);
        Assert.assertSame(instance1, instance2);
    }

    @Test
    public void testRun() throws IOException {
        SearchResponse searchResponse = createTaskSearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(client).execute(eq(MLTaskGetAction.INSTANCE), any(MLTaskGetRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, times(1)).execute(eq(MLTaskGetAction.INSTANCE), any(MLTaskGetRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunWithNoPendingTasks() throws IOException {
        SearchResponse searchResponse = createEmptyTaskSearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(MLTaskGetAction.INSTANCE), any(MLTaskGetRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunWithIndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException(ML_TASK_INDEX));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(MLTaskGetAction.INSTANCE), any(MLTaskGetRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunWithGeneralException() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(MLTaskGetAction.INSTANCE), any(MLTaskGetRequest.class), isA(ActionListener.class));
    }

    private SearchResponse createTaskSearchResponse() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);

        String taskContent = "{\n"
            + "    \"task_type\": \""
            + MLTaskType.BATCH_PREDICTION
            + "\",\n"
            + "    \"state\": \""
            + MLTaskState.RUNNING
            + "\",\n"
            + "    \"function_name\": \""
            + FunctionName.REMOTE
            + "\",\n"
            + "    \"task_id\": \"example-task-id\"\n"
            + "}";

        SearchHit taskHit = SearchHit.fromXContent(TestHelper.parser(taskContent));
        SearchHits hits = new SearchHits(new SearchHit[] { taskHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);

        return searchResponse;
    }

    private SearchResponse createEmptyTaskSearchResponse() {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }
}
