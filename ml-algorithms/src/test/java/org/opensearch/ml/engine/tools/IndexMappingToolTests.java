/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.spi.tools.Tool;

public class IndexMappingToolTests {

    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;
    @Mock
    private MappingMetadata mappingMetadata;
    @Mock
    private GetIndexResponse getIndexResponse;

    private Map<String, String> indexParams;
    private Map<String, String> otherParams;
    private Map<String, String> emptyParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(client.admin()).thenReturn(adminClient);

        IndexMappingTool.Factory.getInstance().init(client);

        indexParams = Map.of("index", "[\"foo\"]");
        otherParams = Map.of("other", "[\"bar\"]");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testRunAsyncNoIndexParams() throws Exception {
        Tool tool = IndexMappingTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(emptyParams, listener);

        future.join();
        assertEquals("There were no results searching the index parameter [null].", future.get());
    }

    @Test
    public void testRunAsyncNoIndices() throws Exception {
        Tool tool = IndexMappingTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(otherParams, listener);

        future.join();
        assertEquals("There were no results searching the index parameter [null].", future.get());
    }

    @Test
    public void testRunAsyncNoResults() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<GetIndexResponse>> actionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).getIndex(any(), actionListenerCaptor.capture());

        Tool tool = IndexMappingTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        when(getIndexResponse.indices()).thenReturn(Strings.EMPTY_ARRAY);

        tool.run(indexParams, listener);
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        future.join();
        assertEquals("There were no results searching the index parameter [[\"foo\"]].", future.get());
    }

    @Test
    public void testRunAsyncIndexMapping() throws Exception {
        String indexName = "foo";

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<GetIndexResponse>> actionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).getIndex(any(), actionListenerCaptor.capture());

        when(getIndexResponse.indices()).thenReturn(new String[] { indexName });
        Settings settings = Settings.builder().put("test.boolean.setting", false).put("test.int.setting", 123).build();
        when(getIndexResponse.settings()).thenReturn(Map.of(indexName, settings));
        String source = "{"
            + "  \"foo\" : {"
            + "    \"mappings\" : {"
            + "      \"year\" : {"
            + "        \"full_name\" : \"year\","
            + "        \"mapping\" : {"
            + "          \"year\" : {"
            + "            \"type\" : \"text\""
            + "          }"
            + "        }"
            + "      },"
            + "      \"age\" : {"
            + "        \"full_name\" : \"age\","
            + "        \"mapping\" : {"
            + "          \"age\" : {"
            + "            \"type\" : \"integer\""
            + "          }"
            + "        }"
            + "      }"
            + "    }"
            + "  }"
            + "}";
        MappingMetadata mapping = new MappingMetadata(indexName, XContentHelper.convertToMap(JsonXContent.jsonXContent, source, true));
        when(getIndexResponse.mappings()).thenReturn(Map.of(indexName, mapping));

        // Now make the call
        Tool tool = IndexMappingTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(indexParams, listener);
        actionListenerCaptor.getValue().onResponse(getIndexResponse);

        future.orTimeout(10, TimeUnit.SECONDS).join();
        String response = future.get();
        List<String> responseList = Arrays.asList(response.trim().split("\\n"));

        assertTrue(responseList.contains("index: foo"));

        assertTrue(responseList.contains("mappings:"));
        assertTrue(
            responseList
                .contains("mappings={year={full_name=year, mapping={year={type=text}}}, age={full_name=age, mapping={age={type=integer}}}}")
        );

        assertTrue(responseList.contains("settings:"));
        assertTrue(responseList.contains("test.boolean.setting=false"));
        assertTrue(responseList.contains("test.int.setting=123"));
    }

    @Test
    public void testTool() {
        Tool tool = IndexMappingTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(IndexMappingTool.TYPE, tool.getName());
        assertTrue(tool.validate(indexParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
    }
}
