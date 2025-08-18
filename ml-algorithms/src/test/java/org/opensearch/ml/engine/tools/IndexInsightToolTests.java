package org.opensearch.ml.engine.tools;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

public class IndexInsightToolTests {
    @Mock
    private Client client;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        IndexInsightTool.Factory.getInstance().init(client);
    }

    @Test
    public void testMLModelsWithDefaultOutputParserAndDefaultResponseField() throws ExecutionException, InterruptedException {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {

            ActionListener<MLIndexInsightGetResponse> actionListener = invocation.getArgument(2);

            actionListener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(new IndexInsight("demo", "demo", IndexInsightTaskStatus.COMPLETED, FIELD_DESCRIPTION, Instant.ofEpochMilli(0))).build());
            return null;
        }).when(client).execute(eq(MLIndexInsightGetAction.INSTANCE), any(), any());

        Tool tool = IndexInsightTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(Map.of("indexName", "demo", "taskType", FIELD_DESCRIPTION.toString()), listener);

        future.join();
        assertEquals("response 1", future.get());
    }
}
