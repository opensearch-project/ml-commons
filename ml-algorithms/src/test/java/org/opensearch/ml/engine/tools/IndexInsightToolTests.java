package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.transport.client.Client;

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
                            new IndexInsight("demo", "demo", IndexInsightTaskStatus.COMPLETED, FIELD_DESCRIPTION, Instant.ofEpochMilli(0), null)
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
            Instant.ofEpochMilli(0), null
        );
        assertEquals(expected.toString(), future.get());
    }

    @Test
    public void testIndexInsightTool_faile() throws ExecutionException, InterruptedException {
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
}
