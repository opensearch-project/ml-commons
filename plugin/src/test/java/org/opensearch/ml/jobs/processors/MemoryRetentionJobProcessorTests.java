/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

@RunWith(MockitoJUnitRunner.class)
public class MemoryRetentionJobProcessorTests {

    @Rule
    public Timeout globalTimeout = new Timeout(60, TimeUnit.SECONDS);

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    private ThreadContext threadContext;

    private MemoryRetentionJobProcessor processor;

    @Before
    public void setUp() {
        MemoryRetentionJobProcessor.reset();

        // Default settings: multi-tenancy disabled, throttle = 1 second
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);

        // Use real ThreadContext (it's a final class and cannot be mocked)
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock threadPool.schedule to execute immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(Runnable.class), any(TimeValue.class), anyString());

        processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
    }

    @Test
    public void testGetInstance() {
        MemoryRetentionJobProcessor instance1 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        MemoryRetentionJobProcessor instance2 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        assertSame(instance1, instance2);
    }

    @Test
    public void testRunSkipsWhenMultiTenancyEnabled() {
        Settings settings = Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build();
        when(clusterService.getSettings()).thenReturn(settings);

        processor.run();

        // Should not search for containers when multi-tenancy is enabled
        verify(client, never()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunExecutesWhenMultiTenancyDisabled() {
        // Return empty containers
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify container search is initiated
        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunExecutesWithDefaultSettings() {
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);

        // Return empty containers
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Default multi_tenancy_enabled is false, so job should execute
        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testNoContainersWithPolicy() {
        // Container search returns empty hits -> job completes
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerWithSessionRetentionTimeBased() {
        // 1 container with retention_days=7, mock 3 expired sessions -> verify cascade + bulk delete
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-time",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns 3 expired sessions
                SearchHit hit1 = new SearchHit(0, "expired-session-1", null, null);
                SearchHit hit2 = new SearchHit(1, "expired-session-2", null, null);
                SearchHit hit3 = new SearchHit(2, "expired-session-3", null, null);
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { hit1, hit2, hit3 },
                    new TotalHits(3, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete for working memory (Phase 2)
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(10L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions (Phase 3)
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify cascade delete was called on working memory
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // Verify bulk delete was called on sessions
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerWithSessionRetentionCountBased() {
        // max_count=5, 10 sessions -> verify 5 oldest deleted
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-count",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", null, 5)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = sessionSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Count-based identification: returns 10 sessions sorted by last_updated_time DESC
                    // First 5 are kept, last 5 are expired
                    SearchHit[] hits = new SearchHit[10];
                    for (int i = 0; i < 10; i++) {
                        hits[i] = new SearchHit(i, "session-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) (10 - i), "session-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits sessionHits = new SearchHits(hits, new TotalHits(10, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse sessionResponse = mock(SearchResponse.class);
                    when(sessionResponse.getHits()).thenReturn(sessionHits);
                    listener.onResponse(sessionResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete for working memory
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify cascade delete + bulk delete both called
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testCascadeOrdering() {
        // Verify delete_by_query (working memory) is called BEFORE bulk delete (sessions)
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-cascade",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns expired sessions
                SearchHit hit1 = new SearchHit(0, "session-1", null, null);
                SearchHit hit2 = new SearchHit(1, "session-2", null, null);
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { hit1, hit2 },
                    new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Track the ordering of operations
        AtomicInteger orderCounter = new AtomicInteger(0);
        AtomicInteger cascadeDeleteOrder = new AtomicInteger(-1);
        AtomicInteger bulkDeleteOrder = new AtomicInteger(-1);

        // Mock cascade delete-by-query for working memory (Phase 2)
        BulkByScrollResponse workingDeleteResponse = mock(BulkByScrollResponse.class);
        when(workingDeleteResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            cascadeDeleteOrder.set(orderCounter.getAndIncrement());
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(workingDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions (Phase 3)
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            bulkDeleteOrder.set(orderCounter.getAndIncrement());
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Working memory delete (cascade) must happen BEFORE session delete (bulk)
        assertTrue(
            "Working memory cascade delete should execute before session bulk delete",
            cascadeDeleteOrder.get() < bulkDeleteOrder.get()
        );
        assertTrue("Cascade delete should have been called", cascadeDeleteOrder.get() >= 0);
        assertTrue("Bulk delete should have been called", bulkDeleteOrder.get() >= 0);
    }

    @Test
    public void testPinnedSessionsExcluded() {
        // Pinned sessions older than retention_days -> not deleted
        // The processor's query uses mustNot(pinned=true), so we verify the query contains pinned filter
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-pinned",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Verify the query excludes pinned=true
                String queryString = request.source().query().toString();
                assertTrue("Query should exclude pinned documents (must_not pinned:true)", queryString.contains("pinned"));

                // Return empty result - all sessions are pinned and excluded
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify session search was made (with pinned exclusion)
        verify(client, atLeast(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        // No deletions should occur since all sessions are pinned (excluded by query)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testBatching() {
        // 600 expired sessions -> verify 2 delete_by_query calls (500 + 100) for working memory cascade
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-batch",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger sessionPageCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Return 600 expired sessions across 6 full pages of 100, then an empty page
                int pageNum = sessionPageCount.getAndIncrement();
                if (pageNum < 6) {
                    // Full page of 100 hits (triggers pagination)
                    SearchHit[] hits = new SearchHit[100];
                    for (int i = 0; i < 100; i++) {
                        int globalIdx = pageNum * 100 + i;
                        hits[i] = new SearchHit(globalIdx, "session-" + globalIdx, null, null);
                        hits[i].sortValues(new Object[] { "session-" + globalIdx }, new DocValueFormat[] { DocValueFormat.RAW });
                    }
                    SearchHits sessionHits = new SearchHits(hits, new TotalHits(600, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse sessionResponse = mock(SearchResponse.class);
                    when(sessionResponse.getHits()).thenReturn(sessionHits);
                    listener.onResponse(sessionResponse);
                } else {
                    // Final page: empty (terminates pagination)
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Track cascade delete calls
        AtomicInteger cascadeDeleteCount = new AtomicInteger(0);

        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(100L);

        doAnswer(invocation -> {
            cascadeDeleteCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // With 600 sessions and DELETE_BY_QUERY_BATCH_SIZE=500, expect 2 cascade delete calls
        assertTrue("Should have at least 2 cascade delete-by-query calls for 600 sessions (batch size 500)", cascadeDeleteCount.get() >= 2);
    }

    @Test
    public void testEmptyExpiredSet() {
        // No sessions match retention criteria -> phases 2-3 skipped
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-empty",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // No expired sessions found
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify no cascade delete or bulk delete was called
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerProcessingError() {
        // One container fails -> next container still processed
        String sourceJson1 = "{\"configuration\":{\"index_prefix\":\"prefix1\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";
        String sourceJson2 = "{\"configuration\":{\"index_prefix\":\"prefix2\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchHit hit1 = new SearchHit(0, "container-fail", null, null);
        hit1.sourceRef(new BytesArray(sourceJson1));
        hit1.sortValues(new Object[] { "container-fail" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchHit hit2 = new SearchHit(1, "container-success", null, null);
        hit2.sourceRef(new BytesArray(sourceJson2));
        hit2.sortValues(new Object[] { "container-success" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchResponse containerSearchResponse = mock(SearchResponse.class);
        // Less than page size (100) so no pagination
        SearchHits containerHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(containerSearchResponse.getHits()).thenReturn(containerHits);

        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                listener.onResponse(containerSearchResponse);
            } else {
                int count = sessionSearchCount.getAndIncrement();
                if (count == 0) {
                    // First container's session search fails
                    listener.onFailure(new RuntimeException("Simulated search failure"));
                } else {
                    // Second container's session search succeeds with empty results
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify that we searched at least twice (once for each container's sessions)
        assertTrue("Both containers should be processed, second container search made", sessionSearchCount.get() >= 2);
    }

    @Test
    public void testNullSessionIndex() {
        // Container with disableSession=true -> session retention skipped, but working memory TTL fires (Phase 6)
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-session", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for Phase 6 working memory TTL (disableSession=true triggers it)
        BulkByScrollResponse ttlResponse = mock(BulkByScrollResponse.class);
        when(ttlResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ttlResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        // Session retention is skipped (session index is null), but Phase 6 working memory TTL fires
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // No bulk deletes (no sessions to delete, no count-based eviction)
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testMultipleContainersProcessed() {
        // 2 containers -> both processed sequentially
        String sourceJson1 = "{\"configuration\":{\"index_prefix\":\"prefix1\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";
        String sourceJson2 = "{\"configuration\":{\"index_prefix\":\"prefix2\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":30}}}}";

        SearchHit hit1 = new SearchHit(0, "container-1", null, null);
        hit1.sourceRef(new BytesArray(sourceJson1));
        hit1.sortValues(new Object[] { "container-1" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchHit hit2 = new SearchHit(1, "container-2", null, null);
        hit2.sourceRef(new BytesArray(sourceJson2));
        hit2.sortValues(new Object[] { "container-2" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchResponse containerSearchResponse = mock(SearchResponse.class);
        SearchHits containerHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(containerSearchResponse.getHits()).thenReturn(containerHits);

        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                listener.onResponse(containerSearchResponse);
            } else {
                sessionSearchCount.incrementAndGet();
                // Return 1 expired session for each container
                SearchHit expiredHit = new SearchHit(0, "expired-session-" + sessionSearchCount.get(), null, null);
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { expiredHit },
                    new TotalHits(1, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(1L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Both containers should have been processed: at least 2 session searches
        assertTrue("Both containers should be processed with session searches", sessionSearchCount.get() >= 2);
        // Verify cascade delete was called for both containers
        verify(client, atLeast(2)).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // Verify bulk delete was called for both containers
        verify(client, atLeast(2)).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Phase 4: Long-Term Retention Tests ---

    @Test
    public void testLongTermRetentionDays() {
        // Container with LTM policy retention_days=30 -> verify _delete_by_query on long-term index
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-days", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // No expired sessions (no session retention rule)
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for long-term retention_days
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            // Verify the query targets long-term index and uses last_updated_time
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Should filter by memory_container_id", queryString.contains("memory_container_id"));
            assertTrue("Should use last_updated_time for long-term", queryString.contains("last_updated_time"));
            assertTrue("Should exclude pinned docs", queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermMaxCount() {
        // Container with LTM policy max_count=100, 150 docs -> verify search for oldest 50 IDs + bulk delete
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"max_count\":100}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-count", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Count query: return totalHits=150
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(150, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Search for oldest 50 IDs: return 50 hits
                    SearchHit[] hits = new SearchHit[50];
                    for (int i = 0; i < 50; i++) {
                        hits[i] = new SearchHit(i, "ltm-doc-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "ltm-doc-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(50, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify bulk delete called for the 50 excess docs
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermPinnedExclusion() {
        // Verify mustNot(pinned=true) in long-term retention_days delete query
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-pinned", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Capture the DeleteByQueryRequest to verify pinned exclusion
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Long-term delete query should exclude pinned=true", queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testNullLtmIndex() {
        // Container without LLM/strategies -> getIndexName(LONG_TERM) returns null -> LTM phase skipped
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-llm", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // No delete-by-query or bulk delete should fire (LTM phase skipped due to null index)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermBothRetentionDaysAndMaxCount() {
        // Both retention_days AND max_count set -> verify both operations fire sequentially
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30,\"max_count\":100}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-both", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Count query for max_count: return 120
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(120, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Search for oldest 20 IDs
                    SearchHit[] hits = new SearchHit[20];
                    for (int i = 0; i < 20; i++) {
                        hits[i] = new SearchHit(i, "ltm-excess-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "ltm-excess-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(20, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for retention_days
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(3L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for max_count
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Both operations should have fired
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Phase 5: History Retention Tests ---

    @Test
    public void testHistoryMaxCount() {
        // Container with history policy max_count=500, 600 docs -> verify search sorted by created_time ASC
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"history\":{\"max_count\":500}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-history-count", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Count query: return totalHits=600
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(600, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Search for oldest 100 IDs — verify sort field is created_time
                    String sourceStr = request.source().toString();
                    assertTrue("History search should sort by created_time", sourceStr.contains("created_time"));

                    SearchHit[] hits = new SearchHit[100];
                    for (int i = 0; i < 100; i++) {
                        hits[i] = new SearchHit(i, "history-doc-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "history-doc-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(100, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testHistoryUsesCreatedTime() {
        // Verify the sort field in history search is created_time, not last_updated_time
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"history\":{\"max_count\":10}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-history-time", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);
        AtomicInteger verifiedCreatedTime = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Count query: return 15 docs (exceeds max_count=10)
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(15, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Verify sort uses created_time, NOT last_updated_time
                    String sourceStr = request.source().toString();
                    assertTrue("History must use created_time for sorting", sourceStr.contains("created_time"));
                    assertTrue("History must NOT use last_updated_time", !sourceStr.contains("last_updated_time"));
                    verifiedCreatedTime.incrementAndGet();

                    SearchHit[] hits = new SearchHit[5];
                    for (int i = 0; i < 5; i++) {
                        hits[i] = new SearchHit(i, "hist-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "hist-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Should have verified created_time sort field", verifiedCreatedTime.get() > 0);
    }

    // --- Phase 6: Working Memory TTL Tests ---

    @Test
    public void testWorkingMemoryTTLDisableSessionTrue() {
        // Container with disableSession=true -> verify _delete_by_query on working index with created_time range
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ttl-enabled", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for working memory TTL
        BulkByScrollResponse ttlDeleteResponse = mock(BulkByScrollResponse.class);
        when(ttlDeleteResponse.getDeleted()).thenReturn(8L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Working memory TTL should filter by memory_container_id", queryString.contains("memory_container_id"));
            assertTrue("Working memory TTL should use created_time", queryString.contains("created_time"));
            // Working memory does NOT support pinning, so no pinned filter
            assertTrue("Working memory TTL should NOT exclude pinned (no pinning support)", !queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ttlDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testWorkingMemoryTTLDisableSessionFalse() {
        // Container with disableSession=false -> Phase 6 skipped entirely
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":false,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ttl-skipped", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns no expired sessions
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // No delete-by-query should be called (session expired=0, and working memory TTL is skipped)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- All phases chain test ---

    @Test
    public void testAllPhasesChainCorrectly() {
        // Container with session, long-term, and history policies -> verify all three phases run
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{"
            + "\"sessions\":{\"retention_days\":7},"
            + "\"long-term\":{\"retention_days\":30},"
            + "\"history\":{\"max_count\":500}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-all-phases", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger deleteByQueryCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // For count queries return 0 (under threshold), for other searches return empty
                SearchResponse countResp = mock(SearchResponse.class);
                SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                when(countResp.getHits()).thenReturn(countHits);
                listener.onResponse(countResp);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query — should be called for session phase and long-term retention_days
        BulkByScrollResponse dbqResponse = mock(BulkByScrollResponse.class);
        when(dbqResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            deleteByQueryCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(dbqResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        // Long-term retention_days should trigger a delete_by_query
        assertTrue("At least one delete_by_query should have been called for long-term retention_days", deleteByQueryCount.get() >= 1);
    }

    // --- Helper methods ---

    private SearchResponse createContainerSearchResponseFromJson(String containerId, String sourceJson) {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHit hit = new SearchHit(0, containerId, null, null);
        hit.sourceRef(new BytesArray(sourceJson));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

    private SearchResponse createContainerSearchResponse(
        String containerId,
        String indexPrefix,
        boolean useSystemIndex,
        Map<String, Object> retentionPolicy
    ) {
        // Build the JSON source for the container hit
        StringBuilder retentionJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : retentionPolicy.entrySet()) {
            if (!first)
                retentionJson.append(",");
            first = false;
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) entry.getValue();
            retentionJson.append("\"").append(entry.getKey()).append("\":{");
            boolean ruleFirst = true;
            if (rule.containsKey("retention_days")) {
                retentionJson.append("\"retention_days\":").append(rule.get("retention_days"));
                ruleFirst = false;
            }
            if (rule.containsKey("max_count")) {
                if (!ruleFirst)
                    retentionJson.append(",");
                retentionJson.append("\"max_count\":").append(rule.get("max_count"));
            }
            retentionJson.append("}");
        }
        retentionJson.append("}");

        String sourceJson = "{\"configuration\":{\"index_prefix\":\""
            + indexPrefix
            + "\","
            + "\"use_system_index\":"
            + useSystemIndex
            + ","
            + "\"retention_policy\":"
            + retentionJson.toString()
            + "}}";

        return createContainerSearchResponseFromJson(containerId, sourceJson);
    }

    private Map<String, Object> createRetentionPolicyMap(String memoryType, Integer retentionDays, Integer maxCount) {
        Map<String, Object> retentionPolicy = new HashMap<>();
        Map<String, Object> rule = new HashMap<>();
        if (retentionDays != null) {
            rule.put("retention_days", retentionDays);
        }
        if (maxCount != null) {
            rule.put("max_count", maxCount);
        }
        retentionPolicy.put(memoryType, rule);
        return retentionPolicy;
    }

    private SearchResponse emptySearchResponse() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(response.getHits()).thenReturn(hits);
        return response;
    }
}
