/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLBatchTaskUpdateJobRunnerTests {

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private JobExecutionContext jobExecutionContext;

    private LockService lockService;

    @Mock
    private MLBatchTaskUpdateJobParameter jobParameter;

    private MLBatchTaskUpdateJobRunner jobRunner;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        jobRunner = MLBatchTaskUpdateJobRunner.getJobRunnerInstance();
        jobRunner.initialize(clusterService, threadPool, client);

        lockService = new LockService(client, clusterService);
        when(jobExecutionContext.getLockService()).thenReturn(lockService);
    }

    @Ignore
    @Test
    public void testRunJobWithoutInitialization() {
        MLBatchTaskUpdateJobRunner uninitializedRunner = MLBatchTaskUpdateJobRunner.getJobRunnerInstance();
        AssertionError exception = Assert.assertThrows(AssertionError.class, () -> {
            uninitializedRunner.runJob(jobParameter, jobExecutionContext);
        });
        Assert.assertEquals("this instance is not initialized", exception.getMessage());
    }

    @Ignore
    @Test
    public void testRunJobFailedToAcquireLock() {

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());

        jobRunner.runJob(jobParameter, jobExecutionContext);

        verify(jobExecutionContext).getLockService();
        verifyNoMoreInteractions(client);
    }

    @Ignore
    @Test
    public void testRunJobWithLockAcquisitionException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to acquire lock"));
            return null;
        }).when(client).get(any(), any());

        Assert.assertThrows(IllegalStateException.class, () -> { jobRunner.runJob(jobParameter, jobExecutionContext); });

        verify(jobExecutionContext).getLockService();
        verifyNoMoreInteractions(client);
    }

    @Ignore
    @Test
    public void testRunJobWithTasksFound() throws IOException {
        SearchResponse searchResponse = createTaskSearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        when(jobExecutionContext.getLockService()).thenReturn(lockService);

        jobRunner.runJob(jobParameter, jobExecutionContext);

        verify(client).search(any(), isA(ActionListener.class));
        verify(lockService).acquireLock(any(), any(), any());
    }

    private SearchResponse createTaskSearchResponse() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);

        String taskContent = "{\n"
            + "    \"task_type\": \"BATCH_PREDICTION\",\n"
            + "    \"state\": \"RUNNING\",\n"
            + "    \"function_name\": \"REMOTE\",\n"
            + "    \"task_id\": \"example-task-id\"\n"
            + "}";

        SearchHit taskHit = SearchHit.fromXContent(TestHelper.parser(taskContent));

        SearchHits hits = new SearchHits(new SearchHit[] { taskHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);

        when(searchResponse.getHits()).thenReturn(hits);

        return searchResponse;
    }

}
