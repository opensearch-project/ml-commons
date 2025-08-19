package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.transport.client.Client;

public class LogRelatedIndexCheckTaskTests {

    private Client client;
    private LogRelatedIndexCheckTask task;

    @Before
    public void setUp() {
        client = mock(Client.class);
        task = new LogRelatedIndexCheckTask("test-index", client);
    }

    // parseCheckResponse declared method
    private Map<String, Object> invokeParse(LogRelatedIndexCheckTask t, String resp) {
        try {
            Method method = LogRelatedIndexCheckTask.class.getDeclaredMethod("parseCheckResponse", String.class);
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(t, resp);
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
}
