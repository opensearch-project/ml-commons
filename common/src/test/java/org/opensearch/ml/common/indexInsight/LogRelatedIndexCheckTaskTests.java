package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLConfigFailure;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLConfigSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLExecuteFailure;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockMLExecuteSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockUpdateSuccess;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class LogRelatedIndexCheckTaskTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Client client;
    private SdkClient sdkClient;
    private LogRelatedIndexCheckTask task;
    private ThreadContext threadContext;
    private ThreadPool threadPool;

    @Before
    public void setUp() {
        client = mock(Client.class);
        sdkClient = mock(SdkClient.class);
        task = new LogRelatedIndexCheckTask("test-index", client, sdkClient);
        threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    private void mockSearchResponse() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchHit hit = new SearchHit(1);
            SearchHit[] hitArray = new SearchHit[] { hit };
            SearchHits hits = new SearchHits(hitArray, new TotalHits(hitArray.length, Relation.EQUAL_TO), 1.0f);
            SearchResponse resp = mock(SearchResponse.class);
            when(resp.getHits()).thenReturn(hits);
            listener.onResponse(resp);
            return null;
        }).when(client).search(any(), any(ActionListener.class));
    }

    // parseCheckResponse declared method
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeParse(LogRelatedIndexCheckTask t, String resp) {
        try {
            Method method = LogRelatedIndexCheckTask.class.getDeclaredMethod("parseCheckResponse", String.class);
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(t, resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // collectSampleDocString declared method
    private void invokeCollect(LogRelatedIndexCheckTask t, ActionListener<String> listener) {
        try {
            Method method = LogRelatedIndexCheckTask.class.getDeclaredMethod("collectSampleDocString", ActionListener.class);
            method.setAccessible(true);
            method.invoke(t, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void parseCheckResponse_validJson_returnsParsedMap() {
        String resp =
            "<RCA_analysis>{\"is_log_index\":true,\"log_message_field\":\"message\",\"trace_id_field\":\"traceId\"}</RCA_analysis>";
        Map<String, Object> message = invokeParse(task, resp);

        assertTrue((Boolean) message.get("is_log_index"));
        assertEquals("message", message.get("log_message_field"));
        assertEquals("traceId", message.get("trace_id_field"));
    }

    @Test
    public void parseCheckResponse_invalid_returnsDefault() {
        String resp = "invalid payload";
        Map<String, Object> message = invokeParse(task, resp);

        assertFalse((Boolean) message.get("is_log_index"));
        assertNull(message.get("log_message_field"));
        assertNull(message.get("trace_id_field"));
    }

    @Test
    public void collectSampleDocString_success_triggersOnResponse() {
        SearchHit hit = new SearchHit(1);
        SearchHit[] hitArray = new SearchHit[] { hit };
        SearchHits hits = new SearchHits(hitArray, new TotalHits(hitArray.length, Relation.EQUAL_TO), 1.0f);

        SearchResponse resp = mock(SearchResponse.class);
        when(resp.getHits()).thenReturn(hits);

        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(resp);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        invokeCollect(task, listener);

        verify(listener, times(1)).onResponse(anyString());
    }

    @Test
    public void collectSampleDocString_failure_triggersOnFailure() {
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("search failed"));
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        invokeCollect(task, listener);

        verify(listener, times(1)).onFailure(any(RuntimeException.class));
    }

    @Test
    public void parseCheckResponse_empty_returnsDefault() {
        Map<String, Object> message = invokeParse(task, "");
        assertFalse((Boolean) message.get("is_log_index"));
        assertNull(message.get("log_message_field"));
        assertNull(message.get("trace_id_field"));
    }

    @Test
    public void parseCheckResponse_missingTag_returnsDefault() {
        String resp = "{\"is_log_index\":true,\"log_message_field\":\"msg\"}";
        Map<String, Object> message = invokeParse(task, resp);
        assertFalse((Boolean) message.get("is_log_index"));
        assertNull(message.get("log_message_field"));
        assertNull(message.get("trace_id_field"));
    }

    @Test
    public void parseCheckResponse_malformedJson_returnsDefault() {
        String resp = "<RCA_analysis>{bad json}</RCA_analysis>";
        Map<String, Object> message = invokeParse(task, resp);
        assertFalse((Boolean) message.get("is_log_index"));
        assertNull(message.get("log_message_field"));
        assertNull(message.get("trace_id_field"));
    }

    @Test
    public void collectSampleDocString_nullHits_triggersOnFailure() {
        SearchResponse resp = mock(SearchResponse.class);
        when(resp.getHits()).thenReturn(null);

        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(resp);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        invokeCollect(task, listener);

        verify(listener, times(1)).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(anyString());
    }

    @Test
    public void collectSampleDocString_noHitsArray_triggersOnResponse() {
        SearchHits noHits = new SearchHits(new SearchHit[0], new TotalHits(0, Relation.EQUAL_TO), 1.0f);
        SearchResponse resp = mock(SearchResponse.class);
        when(resp.getHits()).thenReturn(noHits);

        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(resp);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        invokeCollect(task, listener);

        verify(listener, times(1)).onResponse(anyString());
    }

    @Test
    public void collectSampleDocString_multipleHits_triggersOnResponse() {
        SearchHit h1 = new SearchHit(1);
        SearchHit h2 = new SearchHit(2);
        SearchHits hits = new SearchHits(new SearchHit[] { h1, h2 }, new TotalHits(2, Relation.EQUAL_TO), 1.0f);
        SearchResponse resp = mock(SearchResponse.class);
        when(resp.getHits()).thenReturn(hits);

        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(resp);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);

        invokeCollect(task, listener);

        verify(listener, times(1)).onResponse(anyString());
    }

    @Test
    public void testGetPrerequisites() {
        assertTrue(task.getPrerequisites().isEmpty());
    }

    @Test
    public void testCreatePrerequisiteTask_ThrowsException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("LogRelatedIndexCheckTask has no prerequisites");
        task.createPrerequisiteTask(MLIndexInsightType.STATISTICAL_DATA);
    }

    @Test
    public void testRunTask_Success() {
        mockSearchResponse();
        // Mock getAgentIdToRun
        mockMLConfigSuccess(client);
        // Mock callLLMWithAgent
        String response = "<RCA_analysis>{\"is_log_index\":false,\"log_message_field\":null,\"trace_id_field\":null}</RCA_analysis>";
        mockMLExecuteSuccess(client, response);

        // Mock update call for saveResult
        mockUpdateSuccess(sdkClient);

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(captor.capture());
        IndexInsight insight = captor.getValue();
        assertEquals(MLIndexInsightType.LOG_RELATED_INDEX_CHECK, insight.getTaskType());
        assertTrue(insight.getContent().contains("{\"is_log_index\":false,\"log_message_field\":null,\"trace_id_field\":null}"));
    }

    @Test
    public void testRunTask_MLConfigRetrievalFailure() {
        mockSearchResponse();
        // Mock getAgentIdToRun
        mockMLConfigFailure(client, "Config not found");

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<IllegalStateException> captor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(listener).onFailure(captor.capture());

        IllegalStateException exception = captor.getValue();
        assertEquals("Config not found", exception.getMessage());
    }

    @Test
    public void testRunTask_MLExecuteFailure() {
        mockSearchResponse();
        // Mock getAgentIdToRun
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);
        mockMLExecuteFailure(client, "ML execution failed");

        ActionListener<IndexInsight> listener = mock(ActionListener.class);
        task.runTask("storage-index", "tenant-id", listener);

        ArgumentCaptor<RuntimeException> captor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(listener).onFailure(captor.capture());

        RuntimeException exception = captor.getValue();
        assertEquals("ML execution failed", exception.getMessage());
    }
}
