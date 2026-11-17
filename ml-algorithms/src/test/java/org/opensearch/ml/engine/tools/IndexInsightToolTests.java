/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.transport.client.Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IndexInsightToolTests {
    @Mock
    private Client client;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        IndexInsightTool.Factory.getInstance().init(client);
    }

    @Test
    public void testIndexInsightTool_success() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {

            ActionListener<MLIndexInsightGetResponse> actionListener = invocation.getArgument(2);

            actionListener
                .onResponse(
                    MLIndexInsightGetResponse
                        .builder()
                        .indexInsight(
                            new IndexInsight(
                                "demo",
                                "demo",
                                IndexInsightTaskStatus.COMPLETED,
                                FIELD_DESCRIPTION,
                                Instant.ofEpochMilli(0),
                                null
                            )
                        )
                        .build()
                );
            return null;
        }).when(client).execute(eq(MLIndexInsightGetAction.INSTANCE), any(), any());

        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(Map.of("indexName", "demo", "taskType", FIELD_DESCRIPTION.toString()), listener);

        future.join();
        IndexInsight expected = new IndexInsight(
            "demo",
            "demo",
            IndexInsightTaskStatus.COMPLETED,
            FIELD_DESCRIPTION,
            Instant.ofEpochMilli(0),
            null
        );
        assertEquals(expected.toString(), future.get());
    }

    @Test
    public void testIndexInsightTool_fail() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {

            ActionListener<MLIndexInsightGetResponse> actionListener = invocation.getArgument(2);

            actionListener.onFailure(new RuntimeException("fail to get index insight"));
            return null;
        }).when(client).execute(eq(MLIndexInsightGetAction.INSTANCE), any(), any());

        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(Map.of("indexName", "demo", "taskType", FIELD_DESCRIPTION.toString()), listener);

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> { future.join(); });

        assertEquals("fail to get index insight", runtimeException.getCause().getMessage());
    }

    @Test
    public void testIndexInsightTool_failDueToRunType() throws ExecutionException, InterruptedException {
        doAnswer(invocation -> {

            ActionListener<MLIndexInsightGetResponse> actionListener = invocation.getArgument(2);

            actionListener.onFailure(new RuntimeException("fail to get index insight"));
            return null;
        }).when(client).execute(eq(MLIndexInsightGetAction.INSTANCE), any(), any());

        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(Map.of("indexName", "demo", "taskType", "wrong type"), listener);

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> { future.join(); });

        assertEquals("Wrong index insight type", runtimeException.getCause().getMessage());
    }

    @Test
    public void testTool() {
        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        assertEquals(IndexInsightTool.TYPE, tool.getName());
        assertEquals(IndexInsightTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertEquals(IndexInsightTool.DEFAULT_DESCRIPTION, tool.getDescription());
    }

    @Test
    public void testDefaultInputSchema_declaresTheParametersRunReads() {
        JsonObject schema = JsonParser.parseString(IndexInsightTool.DEFAULT_INPUT_SCHEMA).getAsJsonObject();

        assertEquals("object", schema.get("type").getAsString());
        JsonObject properties = schema.getAsJsonObject("properties");
        assertEquals(Set.of("indexName", "taskType"), properties.keySet());
        assertEquals("string", properties.getAsJsonObject("indexName").get("type").getAsString());

        Set<String> required = new HashSet<>();
        schema.getAsJsonArray("required").forEach(e -> required.add(e.getAsString()));
        assertEquals(Set.of("indexName", "taskType"), required);

        JsonArray taskTypes = properties.getAsJsonObject("taskType").getAsJsonArray("enum");
        Set<String> enumValues = new HashSet<>();
        for (JsonElement taskType : taskTypes) {
            enumValues.add(taskType.getAsString());
            MLIndexInsightType.fromString(taskType.getAsString());
        }
        assertEquals(Set.of("STATISTICAL_DATA", "FIELD_DESCRIPTION", "LOG_RELATED_INDEX_CHECK"), enumValues);
    }

    @Test
    public void testTool_hasDefaultAttributes() {
        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of());

        assertEquals(IndexInsightTool.DEFAULT_INPUT_SCHEMA, tool.getAttributes().get(TOOL_INPUT_SCHEMA_FIELD));
        assertEquals(false, tool.getAttributes().get(IndexInsightTool.STRICT_FIELD));
        assertEquals(IndexInsightTool.DEFAULT_ATTRIBUTES, IndexInsightTool.Factory.getInstance().getDefaultAttributes());

        // Agent tool spec attributes are merged into the tool's attributes, so they must be mutable
        tool.getAttributes().put(TOOL_INPUT_SCHEMA_FIELD, "{}");
        assertEquals("{}", tool.getAttributes().get(TOOL_INPUT_SCHEMA_FIELD));
        assertEquals(IndexInsightTool.DEFAULT_INPUT_SCHEMA, IndexInsightTool.DEFAULT_ATTRIBUTES.get(TOOL_INPUT_SCHEMA_FIELD));
    }

    @Test
    public void testIndexInsightTool_runWithDefaultSchemaParameters() {
        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of());
        // Arguments as a function-calling LLM fills them from the default schema, passed in "input"
        tool.run(Map.of("input", "{\"indexName\": \"demo\", \"taskType\": \"STATISTICAL_DATA\"}"), ActionListener.wrap(r -> {}, e -> {}));

        ArgumentCaptor<MLIndexInsightGetRequest> captor = ArgumentCaptor.forClass(MLIndexInsightGetRequest.class);
        verify(client).execute(eq(MLIndexInsightGetAction.INSTANCE), captor.capture(), any());
        assertEquals("demo", captor.getValue().getIndexName());
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, captor.getValue().getTargetIndexInsight());
    }
}
