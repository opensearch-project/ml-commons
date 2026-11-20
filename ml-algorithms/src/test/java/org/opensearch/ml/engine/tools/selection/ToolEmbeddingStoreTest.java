/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class ToolEmbeddingStoreTest {

    private Client client;
    private ThreadContext threadContext;
    private AtomicLong clock;
    private ToolEmbeddingStore.EmbeddingCache cache;
    private ToolEmbeddingStore store;

    @Before
    public void setUp() {
        client = mock(Client.class);
        ThreadPool threadPool = mock(ThreadPool.class);
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        clock = new AtomicLong();
        // A fresh cache per test: the production store shares one cache per node.
        cache = new ToolEmbeddingStore.EmbeddingCache(
            ToolEmbeddingStore.MAX_CACHE_BYTES,
            ToolEmbeddingStore.EXPIRE_AFTER_WRITE,
            clock::get
        );
        store = new ToolEmbeddingStore(client, cache);
    }

    /** Stubs the embedding model to return one vector per input doc, in order. */
    private void stubPredictReturning(float[]... vectors) {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            List<ModelTensors> outputs = new ArrayList<>();
            for (float[] vector : vectors) {
                Number[] data = new Number[vector.length];
                for (int i = 0; i < vector.length; i++) {
                    data[i] = vector[i];
                }
                ModelTensor tensor = ModelTensor
                    .builder()
                    .name("sentence_embedding")
                    .data(data)
                    .shape(new long[] { 1, data.length })
                    .dataType(MLResultDataType.FLOAT32)
                    .build();
                outputs.add(new ModelTensors(List.of(tensor)));
            }
            listener.onResponse(new MLTaskResponse(new ModelTensorOutput(outputs)));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    /** Stubs the embedding model to return one vector per input doc, however many docs a call carries. */
    private void stubPredictOneVectorPerDoc() {
        doAnswer(invocation -> {
            MLPredictionTaskRequest request = invocation.getArgument(1);
            int docs = ((TextDocsInputDataSet) request.getMlInput().getInputDataset()).getDocs().size();
            float[][] vectors = new float[docs][];
            for (int i = 0; i < docs; i++) {
                vectors[i] = new float[] { 1f, i };
            }
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(responseWith(vectors));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    private static List<ToolDescriptor> tools(int count) {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            descriptors.add(ToolDescriptor.of("tool" + i, "description " + i));
        }
        return descriptors;
    }

    private void stubPredictFailing(String message) {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException(message));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    private Map<String, float[]> getToolEmbeddings(List<ToolDescriptor> descriptors) {
        AtomicReference<Map<String, float[]>> result = new AtomicReference<>();
        store.getToolEmbeddings(descriptors, "embed-model", null, ActionListener.wrap(result::set, e -> {
            throw new AssertionError("unexpected failure", e);
        }));
        return result.get();
    }

    /** Captures predict listeners without answering, so a test controls when each batch completes. */
    private List<ActionListener<MLTaskResponse>> stubPredictPending() {
        List<ActionListener<MLTaskResponse>> pending = new ArrayList<>();
        doAnswer(invocation -> {
            pending.add(invocation.getArgument(2));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());
        return pending;
    }

    private static MLTaskResponse responseWith(float[]... vectors) {
        List<ModelTensors> outputs = new ArrayList<>();
        for (float[] vector : vectors) {
            Number[] data = new Number[vector.length];
            for (int i = 0; i < vector.length; i++) {
                data[i] = vector[i];
            }
            outputs
                .add(
                    new ModelTensors(
                        List.of(ModelTensor.builder().name("sentence_embedding").data(data).dataType(MLResultDataType.FLOAT32).build())
                    )
                );
        }
        return new MLTaskResponse(new ModelTensorOutput(outputs));
    }

    @Test
    public void testEmbedQuestionReturnsTheVector() {
        stubPredictReturning(new float[] { 0.1f, 0.2f });
        AtomicReference<float[]> result = new AtomicReference<>();
        store.embedQuestion("why are my search results bad?", "embed-model", null, ActionListener.wrap(result::set, e -> {
            throw new AssertionError(e);
        }));
        assertArrayEquals(new float[] { 0.1f, 0.2f }, result.get(), 1e-6f);
    }

    @Test
    public void testEmbedQuestionSendsTheQuestionAsASingleDoc() {
        stubPredictReturning(new float[] { 1f });
        store.embedQuestion("my question", "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));

        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), captor.capture(), any());
        assertEquals("embed-model", captor.getValue().getModelId());
        assertEquals(List.of("my question"), ((TextDocsInputDataSet) captor.getValue().getMlInput().getInputDataset()).getDocs());
    }

    @Test
    public void testEmbedQuestionIsNeverCached() {
        stubPredictReturning(new float[] { 1f });
        store.embedQuestion("my question", "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        store.embedQuestion("my question", "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        verify(client, times(2)).execute(any(), any(MLPredictionTaskRequest.class), any());
        assertEquals(0, cache.size());
    }

    @Test
    public void testEmbedQuestionPropagatesFailure() {
        stubPredictFailing("model not deployed");
        AtomicReference<Exception> failure = new AtomicReference<>();
        store.embedQuestion("q", "embed-model", null, ActionListener.wrap(v -> {}, failure::set));
        assertEquals("model not deployed", failure.get().getMessage());
    }

    @Test
    public void testEmbedQuestionFailsWhenModelReturnsNoVector() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onResponse(new MLTaskResponse(new ModelTensorOutput(List.of())));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());

        AtomicReference<Exception> failure = new AtomicReference<>();
        store.embedQuestion("q", "embed-model", null, ActionListener.wrap(v -> {}, failure::set));
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    public void testEmbedQuestionFailsWhenModelReturnsMoreThanOneVector() {
        stubPredictReturning(new float[] { 1f, 0f }, new float[] { 0f, 1f });

        AtomicReference<Exception> failure = new AtomicReference<>();
        store.embedQuestion("q", "embed-model", null, ActionListener.wrap(v -> {}, failure::set));

        assertEquals("Embedding model [embed-model] returned 2 vector(s) for 1 input(s)", failure.get().getMessage());
    }

    @Test
    public void testDeduplicatesIdenticalDescriptionsIntoOneEmbedCall() {
        stubPredictReturning(new float[] { 1f, 0f });

        Map<String, float[]> result = getToolEmbeddings(List.of(ToolDescriptor.of("a", "same"), ToolDescriptor.of("b", "same")));

        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), captor.capture(), any());
        assertEquals(1, ((TextDocsInputDataSet) captor.getValue().getMlInput().getInputDataset()).getDocs().size());
        // Both tools still get a vector out of the single embedding.
        assertArrayEquals(result.get("a"), result.get("b"), 0f);
    }

    @Test
    public void testAllMissesGoOutInASingleBatchedCall() {
        stubPredictReturning(new float[] { 1f }, new float[] { 2f }, new float[] { 3f }, new float[] { 4f }, new float[] { 5f });
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            descriptors.add(ToolDescriptor.of("tool" + i, "description " + i));
        }

        assertEquals(5, getToolEmbeddings(descriptors).size());
        verify(client, times(1)).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    @Test
    public void testNullDescriptorListSkipsEverything() {
        assertTrue(getToolEmbeddings(null).isEmpty());
        verify(client, never()).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    @Test
    public void testEmbeddingFailurePropagatesSoTheSelectorCanFallOpen() {
        stubPredictFailing("model not deployed");
        AtomicReference<Exception> failure = new AtomicReference<>();
        store.getToolEmbeddings(List.of(ToolDescriptor.of("a", "desc a")), "embed-model", null, ActionListener.wrap(v -> {}, failure::set));
        assertEquals("model not deployed", failure.get().getMessage());
    }

    @Test
    public void testVectorCountMismatchFailsTheBatchAndCachesNothing() {
        // Vectors are matched to inputs by position, so a short list would put vectors on the wrong tools.
        stubPredictReturning(new float[] { 1f, 0f });
        AtomicReference<Exception> failure = new AtomicReference<>();

        store
            .getToolEmbeddings(
                List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b")),
                "embed-model",
                null,
                ActionListener.wrap(v -> {}, failure::set)
            );

        assertEquals("Embedding model [embed-model] returned 1 vector(s) for 2 input(s)", failure.get().getMessage());
        assertEquals(0, cache.size());
        assertEquals(0, cache.inFlightSize());
    }

    @Test
    public void testLargePoolsAreEmbeddedInChunks() {
        stubPredictOneVectorPerDoc();

        Map<String, float[]> result = getToolEmbeddings(tools(70));

        assertEquals(70, result.size());
        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(3)).execute(any(), captor.capture(), any());
        List<Integer> sizes = new ArrayList<>();
        for (MLPredictionTaskRequest request : captor.getAllValues()) {
            sizes.add(((TextDocsInputDataSet) request.getMlInput().getInputDataset()).getDocs().size());
        }
        assertEquals(List.of(32, 32, 6), sizes);
    }

    @Test
    public void testAFailedChunkKeepsTheOthers() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        AtomicReference<Map<String, float[]>> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        store.getToolEmbeddings(tools(40), "embed-model", null, ActionListener.wrap(result::set, failure::set));
        assertEquals(2, pending.size());
        float[][] first = new float[32][];
        for (int i = 0; i < 32; i++) {
            first[i] = new float[] { 1f, i };
        }
        pending.get(0).onResponse(responseWith(first));
        pending.get(1).onFailure(new RuntimeException("batch too large"));

        assertNull(failure.get());
        assertEquals(32, result.get().size());
        assertEquals(32, cache.size());
        assertEquals(0, cache.inFlightSize());
    }

    @Test
    public void testExtractVectorsReadsLocalTensorData() {
        // Mirrors what HuggingfaceTextEmbeddingServingTranslator emits: FLOAT32 data with a
        // {1, length} shape. ModelTensor rejects data without a dataType.
        ModelTensor tensor = ModelTensor
            .builder()
            .name("sentence_embedding")
            .data(new Number[] { 0.5f, 1.5f })
            .shape(new long[] { 1, 2 })
            .dataType(MLResultDataType.FLOAT32)
            .build();
        List<float[]> vectors = ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor)))));
        assertEquals(1, vectors.size());
        assertArrayEquals(new float[] { 0.5f, 1.5f }, vectors.get(0), 1e-6f);
    }

    @Test
    public void testExtractVectorsReadsRemoteDataAsMapUnderEmbedding() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("embedding", List.of(0.25, 0.75))).build();
        List<float[]> vectors = ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor)))));
        assertEquals(1, vectors.size());
        assertArrayEquals(new float[] { 0.25f, 0.75f }, vectors.get(0), 1e-6f);
    }

    @Test
    public void testExtractVectorsReadsEveryEntryOfARemoteDataArray() {
        // The OpenAI/Bedrock batch shape: one tensor, all N embeddings under "data".
        ModelTensor tensor = ModelTensor
            .builder()
            .name("response")
            .dataAsMap(
                Map
                    .of(
                        "data",
                        List
                            .of(
                                Map.of("embedding", List.of(0.1, 0.2)),
                                Map.of("embedding", List.of(0.3, 0.4)),
                                Map.of("embedding", List.of(0.5, 0.6))
                            )
                    )
            )
            .build();
        List<float[]> vectors = ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor)))));
        assertEquals(3, vectors.size());
        assertArrayEquals(new float[] { 0.5f, 0.6f }, vectors.get(2), 1e-6f);
    }

    @Test
    public void testExtractVectorsFlattensMultipleOutputs() {
        ModelTensor a = ModelTensor.builder().name("t").dataAsMap(Map.of("embedding", List.of(1.0, 0.0))).build();
        ModelTensor b = ModelTensor.builder().name("t").dataAsMap(Map.of("embedding", List.of(0.0, 1.0))).build();
        List<float[]> vectors = ToolEmbeddingStore
            .extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(a)), new ModelTensors(List.of(b)))));
        assertEquals(2, vectors.size());
    }

    @Test
    public void testExtractVectorsReturnsEmptyWhenNothingUsable() {
        assertTrue(ToolEmbeddingStore.extractVectors(null).isEmpty());
        assertTrue(ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of())).isEmpty());
        assertTrue(ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of())))).isEmpty());
        ModelTensor empty = ModelTensor.builder().name("x").build();
        assertTrue(ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(empty))))).isEmpty());
        ModelTensor other = ModelTensor.builder().name("x").dataAsMap(Map.of("other", 1)).build();
        assertTrue(ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(other))))).isEmpty());
    }

    @Test
    public void testExtractVectorsSkipsNonNumericEmbedding() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("embedding", List.of("not", "numbers"))).build();
        assertTrue(ToolEmbeddingStore.extractVectors(new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor))))).isEmpty());
    }

    @Test
    public void testSingleOutputCarryingAllEmbeddingsMapsOneToOne() {
        // A remote connector with no post-process function returns one ModelTensors with all N embeddings under "data".
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            ModelTensor tensor = ModelTensor
                .builder()
                .name("response")
                .dataAsMap(Map.of("data", List.of(Map.of("embedding", List.of(1.0, 0.0)), Map.of("embedding", List.of(0.0, 1.0)))))
                .build();
            listener.onResponse(new MLTaskResponse(new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor))))));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());

        Map<String, float[]> result = getToolEmbeddings(List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b")));

        assertEquals(2, result.size());
        assertArrayEquals(new float[] { 1f, 0f }, result.get("a"), 1e-6f);
        assertArrayEquals(new float[] { 0f, 1f }, result.get("b"), 1e-6f);
    }

    @Test
    public void testMultipleTensorsInOneOutputMapOneToOne() {
        // The other remote shape: one ModelTensors carrying N ModelTensor entries.
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            ModelTensor first = ModelTensor.builder().name("t0").dataAsMap(Map.of("embedding", List.of(1.0, 0.0))).build();
            ModelTensor second = ModelTensor.builder().name("t1").dataAsMap(Map.of("embedding", List.of(0.0, 1.0))).build();
            listener.onResponse(new MLTaskResponse(new ModelTensorOutput(List.of(new ModelTensors(List.of(first, second))))));
            return null;
        }).when(client).execute(any(), any(MLPredictionTaskRequest.class), any());

        Map<String, float[]> result = getToolEmbeddings(List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b")));

        assertEquals(2, result.size());
        assertArrayEquals(new float[] { 0f, 1f }, result.get("b"), 1e-6f);
    }

    @Test
    public void testLocalModelShapeStillMapsOneToOne() {
        // The local-model shape: N ModelTensors, one vector each.
        stubPredictReturning(new float[] { 1f, 0f }, new float[] { 0f, 1f });

        Map<String, float[]> result = getToolEmbeddings(List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b")));

        assertEquals(2, result.size());
        assertArrayEquals(new float[] { 0f, 1f }, result.get("b"), 1e-6f);
    }

    @Test
    public void testBlankDescriptionEmbedsTheToolNameInstead() {
        stubPredictReturning(new float[] { 1f, 0f });

        getToolEmbeddings(List.of(ToolDescriptor.of("SearchIndexTool", null)));

        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), captor.capture(), any());
        assertEquals(List.of("SearchIndexTool"), ((TextDocsInputDataSet) captor.getValue().getMlInput().getInputDataset()).getDocs());
    }

    @Test
    public void testTwoDescriptionlessToolsGetSeparateEmbeddings() {
        stubPredictReturning(new float[] { 1f, 0f }, new float[] { 0f, 1f });

        Map<String, float[]> result = getToolEmbeddings(
            List.of(ToolDescriptor.of("SearchIndexTool", null), ToolDescriptor.of("ListIndexTool", null))
        );

        assertEquals(2, result.size());
        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), captor.capture(), any());
        assertEquals(2, ((TextDocsInputDataSet) captor.getValue().getMlInput().getInputDataset()).getDocs().size());
    }

    @Test
    public void testSecondCallIsServedFromTheCache() {
        stubPredictReturning(new float[] { 1f, 0f }, new float[] { 0f, 1f });
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b"));

        getToolEmbeddings(tools);
        Map<String, float[]> second = getToolEmbeddings(tools);

        verify(client, times(1)).execute(any(), any(MLPredictionTaskRequest.class), any());
        assertArrayEquals(new float[] { 1f, 0f }, second.get("a"), 1e-6f);
        assertEquals(2, cache.size());
    }

    @Test
    public void testPartialCacheHitEmbedsOnlyTheMisses() {
        stubPredictReturning(new float[] { 1f, 0f });
        ToolDescriptor cached = ToolDescriptor.of("cached", "desc cached");
        getToolEmbeddings(List.of(cached));

        stubPredictReturning(new float[] { 0f, 1f });
        Map<String, float[]> result = getToolEmbeddings(List.of(cached, ToolDescriptor.of("fresh", "desc fresh")));

        assertEquals(2, result.size());
        assertArrayEquals(new float[] { 1f, 0f }, result.get("cached"), 1e-6f);
        assertArrayEquals(new float[] { 0f, 1f }, result.get("fresh"), 1e-6f);
        ArgumentCaptor<MLPredictionTaskRequest> captor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(2)).execute(any(), captor.capture(), any());
        assertEquals(List.of("desc fresh"), ((TextDocsInputDataSet) captor.getValue().getMlInput().getInputDataset()).getDocs());
    }

    @Test
    public void testTenantsNeverShareEntries() {
        stubPredictReturning(new float[] { 1f, 0f });
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "same description"));
        AtomicReference<Map<String, float[]>> result = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", "tenant-1", ActionListener.wrap(result::set, e -> {}));
        store.getToolEmbeddings(tools, "embed-model", "tenant-2", ActionListener.wrap(result::set, e -> {}));

        verify(client, times(2)).execute(any(), any(MLPredictionTaskRequest.class), any());
        assertEquals(2, cache.size());
    }

    @Test
    public void testChangingTheModelMissesTheCache() {
        stubPredictReturning(new float[] { 1f, 0f });
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));

        store.getToolEmbeddings(tools, "model-1", null, ActionListener.wrap(v -> {}, e -> {}));
        store.getToolEmbeddings(tools, "model-2", null, ActionListener.wrap(v -> {}, e -> {}));

        verify(client, times(2)).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    @Test
    public void testFailureCachesNothingAndTheNextCallRetries() {
        stubPredictFailing("model not deployed");
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        assertEquals(0, cache.size());
        assertEquals(0, cache.inFlightSize());

        stubPredictReturning(new float[] { 1f, 0f });
        assertEquals(1, getToolEmbeddings(tools).size());
        assertEquals(1, cache.size());
    }

    @Test
    public void testCacheIsBoundedByWeight() {
        // Room for roughly two 100-float vectors (400 bytes each plus overhead), not three.
        cache = new ToolEmbeddingStore.EmbeddingCache(1300, ToolEmbeddingStore.EXPIRE_AFTER_WRITE, clock::get);
        store = new ToolEmbeddingStore(client, cache);
        stubPredictReturning(new float[100], new float[100], new float[100]);

        getToolEmbeddings(List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b"), ToolDescriptor.of("c", "desc c")));

        assertTrue(cache.size() < 3);
    }

    @Test
    public void testVectorsExpireAfterWrite() throws InterruptedException {
        cache = new ToolEmbeddingStore.EmbeddingCache(ToolEmbeddingStore.MAX_CACHE_BYTES, TimeValue.timeValueMillis(1), clock::get);
        store = new ToolEmbeddingStore(client, cache);
        stubPredictReturning(new float[] { 1f, 0f });
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));

        getToolEmbeddings(tools);
        Thread.sleep(20);
        getToolEmbeddings(tools);

        verify(client, times(2)).execute(any(), any(MLPredictionTaskRequest.class), any());
    }

    @Test
    public void testWaitingCallerContinuesInItsOwnThreadContext() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        AtomicReference<String> userSeenByWaiter = new AtomicReference<>();

        try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
            threadContext.putHeader("user", "alice");
            store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        }
        try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
            threadContext.putHeader("user", "bob");
            store
                .getToolEmbeddings(
                    tools,
                    "embed-model",
                    null,
                    ActionListener.wrap(v -> userSeenByWaiter.set(threadContext.getHeader("user")), e -> {})
                );
        }
        try (ThreadContext.StoredContext ignored = threadContext.stashContext()) {
            threadContext.putHeader("user", "alice");
            pending.get(0).onResponse(responseWith(new float[] { 1f, 0f }));
            assertEquals("alice", threadContext.getHeader("user"));
        }

        assertEquals("bob", userSeenByWaiter.get());
    }

    @Test
    public void testConcurrentCallerWaitsForTheInFlightBatchInsteadOfEmbeddingAgain() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"), ToolDescriptor.of("b", "desc b"));
        AtomicReference<Map<String, float[]>> warmUp = new AtomicReference<>();
        AtomicReference<Map<String, float[]>> search = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(warmUp::set, e -> {}));
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(search::set, e -> {}));

        assertEquals("the second caller must not start its own batch", 1, pending.size());
        assertNull(search.get());

        pending.get(0).onResponse(responseWith(new float[] { 1f, 0f }, new float[] { 0f, 1f }));

        assertEquals(2, warmUp.get().size());
        assertEquals(2, search.get().size());
        assertArrayEquals(new float[] { 0f, 1f }, search.get().get("b"), 1e-6f);
        assertEquals(0, cache.inFlightSize());
    }

    @Test
    public void testBatchThatNeverCompletesTimesOutForEveryCaller() throws InterruptedException {
        store = new ToolEmbeddingStore(client, cache, TimeUnit.MILLISECONDS.toNanos(50));
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        AtomicReference<Exception> ownerFailure = new AtomicReference<>();
        AtomicReference<Exception> waiterFailure = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, ownerFailure::set));
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, waiterFailure::set));
        assertEquals(1, pending.size());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((ownerFailure.get() == null || waiterFailure.get() == null) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(waiterFailure.get().getMessage().startsWith("Timed out"));
        assertTrue(ownerFailure.get().getMessage().startsWith("Timed out"));

        // The batch itself is untouched: a late answer still lands in the cache.
        pending.get(0).onResponse(responseWith(new float[] { 1f, 0f }));
        assertEquals(1, cache.size());
    }

    @Test
    public void testWaitingCallerFailsWhenTheBatchItWaitsOnFails() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        AtomicReference<Exception> waiterFailure = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, waiterFailure::set));
        pending.get(0).onFailure(new RuntimeException("model not deployed"));

        assertEquals("model not deployed", waiterFailure.get().getMessage());
        assertEquals(0, cache.inFlightSize());
    }

    @Test
    public void testAbandonedInFlightBatchIsClaimedAgain() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        AtomicReference<Map<String, float[]>> second = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        clock.addAndGet(ToolEmbeddingStore.EmbeddingCache.ABANDONED_AFTER_NANOS + TimeUnit.SECONDS.toNanos(1));
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(second::set, e -> {}));

        assertEquals("a stale in-flight entry must not block a new caller", 2, pending.size());
        pending.get(1).onResponse(responseWith(new float[] { 1f, 0f }));
        assertEquals(1, second.get().size());
    }

    @Test
    public void testLateResultFromAnAbandonedBatchDoesNotDisturbTheNewOwner() {
        List<ActionListener<MLTaskResponse>> pending = stubPredictPending();
        List<ToolDescriptor> tools = List.of(ToolDescriptor.of("a", "desc a"));
        AtomicReference<Exception> newOwnerFailure = new AtomicReference<>();
        AtomicReference<Map<String, float[]>> newOwner = new AtomicReference<>();

        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(v -> {}, e -> {}));
        clock.addAndGet(ToolEmbeddingStore.EmbeddingCache.ABANDONED_AFTER_NANOS + 1);
        store.getToolEmbeddings(tools, "embed-model", null, ActionListener.wrap(newOwner::set, newOwnerFailure::set));

        // The abandoned batch fails late; the new owner's entry must stay in flight and unaffected.
        pending.get(0).onFailure(new RuntimeException("late failure"));
        assertNull(newOwnerFailure.get());
        assertEquals(1, cache.inFlightSize());

        pending.get(1).onResponse(responseWith(new float[] { 1f, 0f }));
        assertEquals(1, newOwner.get().size());
        assertEquals(0, cache.inFlightSize());
    }
}
