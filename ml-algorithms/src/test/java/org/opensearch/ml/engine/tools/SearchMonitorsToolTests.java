/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.AdminClient;
import org.opensearch.client.ClusterAdminClient;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;

public class SearchMonitorsToolTests {
    @Mock
    private NodeClient nodeClient;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;
    @Mock
    private ClusterAdminClient clusterAdminClient;

    private Map<String, String> nullParams;
    private Map<String, String> emptyParams;
    private Map<String, String> nonEmptyParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        SearchMonitorsTool.Factory.getInstance().init(nodeClient);

        nullParams = null;
        emptyParams = Collections.emptyMap();
        nonEmptyParams = Map.of("monitorName", "foo");
    }

    @Test
    public void testRunWithNoMonitors() throws Exception {
        Tool tool = SearchMonitorsTool.Factory.getInstance().create(Collections.emptyMap());

        SearchHit[] hits = new SearchHit[0];

        TotalHits totalHits = new TotalHits(hits.length, TotalHits.Relation.EQUAL_TO);

        SearchResponse getMonitorsResponse = new SearchResponse(
            new SearchResponseSections(new SearchHits(hits, totalHits, 0), new Aggregations(new ArrayList<>()), null, false, null, null, 0),
            null,
            0,
            0,
            0,
            0,
            null,
            null
        );
        String expectedResponseStr = String.format("Monitors=[]TotalMonitors=%d", hits.length);

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = Mockito.mock(ActionListener.class);

        doAnswer((invocation) -> {
            ActionListener<SearchResponse> responseListener = invocation.getArgument(2);
            responseListener.onResponse(getMonitorsResponse);
            return null;
        }).when(nodeClient).execute(any(ActionType.class), any(), any());

        tool.run(emptyParams, listener);
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(listener, times(1)).onResponse(responseCaptor.capture());
        assertEquals(expectedResponseStr, responseCaptor.getValue());
    }

    // TODO: add tests to exercise get monitor action vs. search monitor action

    @Test
    public void testRunWithSingleMonitor() throws Exception {
        final String monitorName = "monitor-1";
        final String monitorId = "monitor-1-id";
        Tool tool = SearchMonitorsTool.Factory.getInstance().create(Collections.emptyMap());

        // TODO: explore more detailed monitor response. IndexMonitorRequest translates to a PUT request
        // to the system index, and is the same result as what's returned by the search response in search monitors API.
        // explore converting the response to this to test it

        // IndexMonitorRequest indexedMonitor = new IndexMonitorRequest(
        // "1234",
        // 1L,
        // 2L,
        // WriteRequest.RefreshPolicy.IMMEDIATE,
        // RestRequest.Method.POST,
        // // randomQueryLevelMonitor().copy(inputs = listOf(SearchInput(emptyList(), SearchSourceBuilder())));
        // randomQueryLevelMonitor()
        // );

        XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
        content.startObject();
        content.field("type", "monitor");
        content.field("name", monitorName);
        content.endObject();
        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, monitorId, null, null).sourceRef(BytesReference.bytes(content));

        TotalHits totalHits = new TotalHits(hits.length, TotalHits.Relation.EQUAL_TO);

        SearchResponse getMonitorsResponse = new SearchResponse(
            new SearchResponseSections(new SearchHits(hits, totalHits, 0), new Aggregations(new ArrayList<>()), null, false, null, null, 0),
            null,
            0,
            0,
            0,
            0,
            null,
            null
        );
        String expectedResponseStr = String.format("Monitors=[{id=%s,name=%s}]TotalMonitors=%d", monitorId, monitorName, hits.length);

        @SuppressWarnings("unchecked")
        ActionListener<String> listener = Mockito.mock(ActionListener.class);

        doAnswer((invocation) -> {
            ActionListener<SearchResponse> responseListener = invocation.getArgument(2);
            responseListener.onResponse(getMonitorsResponse);
            return null;
        }).when(nodeClient).execute(any(ActionType.class), any(), any());

        tool.run(emptyParams, listener);
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(listener, times(1)).onResponse(responseCaptor.capture());
        assertEquals(expectedResponseStr, responseCaptor.getValue());
    }

    @Test
    public void testValidate() {
        Tool tool = SearchMonitorsTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(SearchMonitorsTool.TYPE, tool.getType());
        assertTrue(tool.validate(emptyParams));
        assertTrue(tool.validate(nonEmptyParams));
        assertTrue(tool.validate(nullParams));
    }
}
