/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

public class QueryPlanningToolTests {

    @Mock
    private Client client;

    @Mock
    private MLModelTool queryGenerationTool;

    private Map<String, String> validParams;
    private Map<String, String> emptyParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MLModelTool.Factory.getInstance().init(client);
        QueryPlanningTool.Factory.getInstance().init(client);
        validParams = new HashMap<>();
        validParams.put("prompt", "test prompt");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testFactoryCreate() {
        Map<String, Object> map = Map.of(MODEL_ID_FIELD, "test_model_id");
        Tool tool = QueryPlanningTool.Factory.getInstance().create(map);
        assertNotNull(tool);
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
    }

    @Test
    public void testRun() throws ExecutionException, InterruptedException {
        String matchQueryString = "{\"query\":{\"match\":{\"title\":\"wind\"}}}";
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", matchQueryString)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build();
        List<ModelTensors> modelTensorsList = List.of(modelTensors);

        doAnswer(invocation -> {
            ActionListener<List<ModelTensors>> listener = invocation.getArgument(1);
            listener.onResponse(modelTensorsList);
            return null;
        }).when(queryGenerationTool).run(any(), any());

        QueryPlanningTool tool = new QueryPlanningTool(client, "test_model_id", "llmGenerated", queryGenerationTool);
        tool.setOutputParser(o -> {
            List<ModelTensors> outputs = (List<ModelTensors>) o;
            return outputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
        });

        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(future::complete, future::completeExceptionally);
        // test try to update the prompt
        validParams
            .put("prompt", "You are a query generation agent. Generate a dsl query for the following question: ${parameters.query_text}");
        validParams.put("query_text", "help me find some books related to wind");
        tool.run(validParams, listener);

        assertEquals(matchQueryString, future.get());
    }

    @Test
    public void testValidate() {
        Tool tool = QueryPlanningTool.Factory.getInstance().create(Collections.emptyMap());
        assertTrue(tool.validate(validParams));
        assertFalse(tool.validate(emptyParams));
        assertFalse(tool.validate(null));
    }

    @Test
    public void testToolGetters() {
        Tool tool = QueryPlanningTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(QueryPlanningTool.TYPE, tool.getName());
        assertEquals(QueryPlanningTool.TYPE, tool.getType());
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
        assertNull(tool.getVersion());
    }

    @Test
    public void testFactoryGetAllModelKeys() {
        List<String> allModelKeys = QueryPlanningTool.Factory.getInstance().getAllModelKeys();
        assertEquals(List.of(MODEL_ID_FIELD), allModelKeys);
    }
}
