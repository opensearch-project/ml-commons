/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.spi.tools.Tool;

public class VisualizationToolTests {
    @Mock
    private Client client;

    private String searchResponse = "{}";
    private String searchResponseNotFound = "{}";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        VisualizationsTool.Factory.getInstance().init(client);
        try (InputStream searchResponseIns = VisualizationToolTests.class.getResourceAsStream("visualization.json")) {
            if (searchResponseIns != null) {
                searchResponse = new String(searchResponseIns.readAllBytes());
            }
        }
        try (InputStream searchResponseIns = VisualizationToolTests.class.getResourceAsStream("visualization_not_found.json")) {
            if (searchResponseIns != null) {
                searchResponseNotFound = new String(searchResponseIns.readAllBytes());
            }
        }
    }

    @Test
    public void testToolIndexName() {
        VisualizationsTool tool1 = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        Assert.assertEquals(tool1.getIndex(), ".kibana");

        VisualizationsTool tool2 = VisualizationsTool.Factory.getInstance().create(Map.of("index", "test-index"));
        Assert.assertEquals(tool2.getIndex(), "test-index");
    }

    @Test
    public void testTrimPrefix() {
        VisualizationsTool tool = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        Assert.assertEquals(tool.trimIdPrefix(null), "");
        Assert.assertEquals(tool.trimIdPrefix("abc"), "abc");
        Assert.assertEquals(tool.trimIdPrefix("visualization:abc"), "abc");
    }

    @Test
    public void testParameterValidation() {
        VisualizationsTool tool = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        Assert.assertFalse(tool.validate(Collections.emptyMap()));
        Assert.assertFalse(tool.validate(Map.of("input", "")));
        Assert.assertTrue(tool.validate(Map.of("input", "question")));
    }

    @Test
    public void testRunToolWithVisualizationFound() throws Exception {
        Tool tool = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        ArgumentCaptor<ActionListener<SearchResponse>> searchResponseListener = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(client).search(any(SearchRequest.class), searchResponseListener.capture());

        Map<String, String> params = Map.of("input", "Sales by gender");

        tool.run(params, listener);

        SearchResponse response = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, searchResponse)
            );
        searchResponseListener.getValue().onResponse(response);

        future.join();
        assertEquals("Title,Id\n[Ecommerce]Sales by gender,aeb212e0-4c84-11e8-b3d7-01146121b73d\n", future.get());
    }

    @Test
    public void testRunToolWithNoVisualizationFound() throws Exception {
        Tool tool = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        ArgumentCaptor<ActionListener<SearchResponse>> searchResponseListener = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(client).search(any(SearchRequest.class), searchResponseListener.capture());

        Map<String, String> params = Map.of("input", "Sales by gender");

        tool.run(params, listener);

        SearchResponse response = SearchResponse
            .fromXContent(
                JsonXContent.jsonXContent
                    .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, searchResponseNotFound)
            );
        searchResponseListener.getValue().onResponse(response);

        future.join();
        assertEquals("No Visualization found", future.get());
    }

    @Test
    public void testRunToolWithIndexNotExists() throws Exception {
        Tool tool = VisualizationsTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);

        ArgumentCaptor<ActionListener<SearchResponse>> searchResponseListener = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(client).search(any(SearchRequest.class), searchResponseListener.capture());

        Map<String, String> params = Map.of("input", "Sales by gender");

        tool.run(params, listener);

        IndexNotFoundException notFoundException = new IndexNotFoundException("test-index");
        searchResponseListener.getValue().onFailure(notFoundException);

        future.join();
        assertEquals("No Visualization found", future.get());
    }
}
