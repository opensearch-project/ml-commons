/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.opensearch.action.support.ContextPreservingActionListener;
import org.opensearch.common.cache.Cache;
import org.opensearch.common.cache.CacheBuilder;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.transport.client.Client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Produces tool description embeddings from a node-local in-memory cache. The vectors are cheap to
 * recompute, so they are not persisted; the cache key includes the tenant id. Failures are surfaced
 * so the selector can fall open and expose every tool.
 */
@Log4j2
public class ToolEmbeddingStore {

    /** About 4,000 descriptions at 3,072 dimensions, or 16,000 at 768. */
    static final long MAX_CACHE_BYTES = 50L * 1024 * 1024;
    /** Bounds how long vectors outlive a change behind the same model id, such as a new connector. */
    static final TimeValue EXPIRE_AFTER_WRITE = TimeValue.timeValueHours(1);
    /** Descriptions per predict call; below common provider batch limits. */
    static final int EMBEDDING_BATCH_SIZE = 32;

    private static final String EMBEDDING_FIELD = "embedding";
    private static final String DATA_FIELD = "data";

    /** Static because a new store is built for every agent execution. */
    private static final EmbeddingCache NODE_CACHE = new EmbeddingCache(MAX_CACHE_BYTES, EXPIRE_AFTER_WRITE, System::nanoTime);

    private final Client client;
    private final EmbeddingCache cache;
    /** Bounds every wait on a batch, including another run's, so a batch that never completes can't hang a run. */
    private final long waitTimeoutNanos;

    public ToolEmbeddingStore(Client client) {
        this(client, NODE_CACHE);
    }

    ToolEmbeddingStore(Client client, EmbeddingCache cache) {
        this(client, cache, EmbeddingCache.ABANDONED_AFTER_NANOS);
    }

    ToolEmbeddingStore(Client client, EmbeddingCache cache, long waitTimeoutNanos) {
        this.client = client;
        this.cache = cache;
        this.waitTimeoutNanos = waitTimeoutNanos;
    }

    /** Not cached: questions are rarely repeated. */
    public void embedQuestion(String question, String embeddingModelId, String tenantId, ActionListener<float[]> callerListener) {
        ActionListener<float[]> listener = inCallerContext(callerListener);
        predict(List.of(question), embeddingModelId, tenantId, ActionListener.wrap(vectors -> {
            if (vectors.size() != 1) {
                listener.onFailure(countMismatch(embeddingModelId, vectors.size(), 1));
                return;
            }
            listener.onResponse(vectors.get(0));
        }, listener::onFailure));
    }

    /**
     * Tools whose embedding could not be produced are absent from the map, never mapped to null.
     * Fails only when an embedding failed and no tool has a vector.
     */
    public void getToolEmbeddings(
        List<ToolDescriptor> descriptors,
        String embeddingModelId,
        String tenantId,
        ActionListener<Map<String, float[]>> callerListener
    ) {
        ActionListener<Map<String, float[]>> listener = inCallerContext(callerListener);
        if (descriptors == null || descriptors.isEmpty()) {
            listener.onResponse(Map.of());
            return;
        }

        // Tools with identical descriptions share a key and cost one embedding.
        Map<String, ToolDescriptor> byCacheKey = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : descriptors) {
            byCacheKey.putIfAbsent(descriptor.cacheKey(embeddingModelId, tenantId), descriptor);
        }

        EmbeddingCache.Claim claim = cache.claim(byCacheKey.keySet());
        Map<String, float[]> byKey = new ConcurrentHashMap<>(claim.getHits());
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        claim.getAwaited().forEach((key, future) -> pending.add(bounded(future).handle((vector, error) -> {
            if (error != null) {
                firstError.compareAndSet(null, describe(error));
            } else if (vector != null) {
                byKey.put(key, vector);
            }
            return null;
        })));
        for (CompletableFuture<Map<String, float[]>> chunk : embedClaimed(claim.getMine(), byCacheKey, embeddingModelId, tenantId)) {
            pending.add(bounded(chunk).handle((vectors, error) -> {
                if (error != null) {
                    firstError.compareAndSet(null, describe(error));
                } else {
                    byKey.putAll(vectors);
                }
                return null;
            }));
        }
        CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, unexpected) -> {
            try {
                Throwable error = unexpected != null ? unexpected : firstError.get();
                if (error != null && byKey.isEmpty()) {
                    listener.onFailure(unwrap(error));
                    return;
                }
                if (error != null) {
                    log.warn("Some tool descriptions could not be embedded; searching the rest", unwrap(error));
                }
                listener.onResponse(toToolNameMap(descriptors, byKey, embeddingModelId, tenantId));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        });
    }

    /** Embeds the claimed keys in chunks of {@link #EMBEDDING_BATCH_SIZE}, each cached as it completes. */
    private List<CompletableFuture<Map<String, float[]>>> embedClaimed(
        Map<String, EmbeddingCache.InFlight> claimed,
        Map<String, ToolDescriptor> byCacheKey,
        String embeddingModelId,
        String tenantId
    ) {
        List<String> claimedKeys = new ArrayList<>(claimed.keySet());
        List<CompletableFuture<Map<String, float[]>>> chunks = new ArrayList<>();
        for (int from = 0; from < claimedKeys.size(); from += EMBEDDING_BATCH_SIZE) {
            List<String> chunk = claimedKeys.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, claimedKeys.size()));
            chunks.add(embedChunk(chunk, claimed, byCacheKey, embeddingModelId, tenantId));
        }
        return chunks;
    }

    /** Every key in the chunk is released whatever the outcome, so none stays in flight. */
    private CompletableFuture<Map<String, float[]>> embedChunk(
        List<String> keys,
        Map<String, EmbeddingCache.InFlight> claimed,
        Map<String, ToolDescriptor> byCacheKey,
        String embeddingModelId,
        String tenantId
    ) {
        CompletableFuture<Map<String, float[]>> result = new CompletableFuture<>();
        List<String> texts = new ArrayList<>(keys.size());
        for (String key : keys) {
            texts.add(byCacheKey.get(key).embeddingText());
        }
        try {
            predict(texts, embeddingModelId, tenantId, ActionListener.wrap(vectors -> {
                // Vectors are matched to inputs by position, so any other count would mislabel them.
                if (vectors.size() != keys.size()) {
                    failChunk(keys, claimed, result, countMismatch(embeddingModelId, vectors.size(), keys.size()));
                    return;
                }
                Map<String, float[]> embedded = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    embedded.put(keys.get(i), vectors.get(i));
                    cache.publish(keys.get(i), claimed.get(keys.get(i)), vectors.get(i));
                }
                log.debug("Embedded {} tool description(s) into the tool search cache", embedded.size());
                result.complete(embedded);
            }, e -> failChunk(keys, claimed, result, e)));
        } catch (Exception e) {
            failChunk(keys, claimed, result, e);
        }
        return result;
    }

    private void failChunk(
        List<String> keys,
        Map<String, EmbeddingCache.InFlight> claimed,
        CompletableFuture<Map<String, float[]>> result,
        Exception error
    ) {
        for (String key : keys) {
            cache.fail(key, claimed.get(key), error);
        }
        result.completeExceptionally(error);
    }

    private static IllegalStateException countMismatch(String embeddingModelId, int vectors, int inputs) {
        return new IllegalStateException(
            "Embedding model [" + embeddingModelId + "] returned " + vectors + " vector(s) for " + inputs + " input(s)"
        );
    }

    /** A copy, so timing out this caller's wait doesn't fail the batch for anyone else. */
    private <T> CompletableFuture<T> bounded(CompletableFuture<T> future) {
        return future.copy().orTimeout(waitTimeoutNanos, TimeUnit.NANOSECONDS);
    }

    private Throwable describe(Throwable error) {
        if (unwrap(error) instanceof TimeoutException) {
            return new IllegalStateException(
                "Timed out after " + TimeUnit.NANOSECONDS.toSeconds(waitTimeoutNanos) + "s waiting for tool description embeddings"
            );
        }
        return error;
    }

    /** A shared batch may complete on another run's thread, so the caller's context is restored first. */
    private <T> ActionListener<T> inCallerContext(ActionListener<T> listener) {
        return ContextPreservingActionListener.wrapPreservingContext(listener, client.threadPool().getThreadContext());
    }

    private static Exception unwrap(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
    }

    private Map<String, float[]> toToolNameMap(
        List<ToolDescriptor> descriptors,
        Map<String, float[]> byKey,
        String embeddingModelId,
        String tenantId
    ) {
        Map<String, float[]> byToolName = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : descriptors) {
            float[] vector = byKey.get(descriptor.cacheKey(embeddingModelId, tenantId));
            if (vector != null) {
                byToolName.put(descriptor.getName(), vector);
            }
        }
        return byToolName;
    }

    private void predict(List<String> docs, String embeddingModelId, String tenantId, ActionListener<List<float[]>> listener) {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(TextDocsInputDataSet.builder().docs(docs).build())
            .build();
        MLPredictionTaskRequest request = new MLPredictionTaskRequest(embeddingModelId, mlInput, null, tenantId);

        client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(response -> {
            MLOutput output = ((MLTaskResponse) response).getOutput();
            if (!(output instanceof ModelTensorOutput)) {
                listener.onFailure(new IllegalStateException("Embedding model [" + embeddingModelId + "] did not return tensor output"));
                return;
            }
            List<float[]> vectors = extractVectors((ModelTensorOutput) output);
            listener.onResponse(vectors);
        }, listener::onFailure));
    }

    /**
     * Flattens a model's output into one vector per input document. The shape varies by model:
     * <ul>
     *   <li>local pretrained models return N {@link ModelTensors}, each holding one tensor with
     *       {@code data} populated;</li>
     *   <li>remote connector models without a post-process function typically return a
     *       <b>single</b> {@link ModelTensors} whose {@code dataAsMap} carries all N embeddings
     *       under {@code data};</li>
     *   <li>others return a single {@link ModelTensors} holding N separate tensors.</li>
     * </ul>
     */
    static List<float[]> extractVectors(ModelTensorOutput tensorOutput) {
        List<float[]> vectors = new ArrayList<>();
        if (tensorOutput == null || tensorOutput.getMlModelOutputs() == null) {
            return vectors;
        }
        for (ModelTensors tensors : tensorOutput.getMlModelOutputs()) {
            if (tensors == null || tensors.getMlModelTensors() == null) {
                continue;
            }
            for (ModelTensor tensor : tensors.getMlModelTensors()) {
                vectors.addAll(extractAllFromTensor(tensor));
            }
        }
        return vectors;
    }

    /** One tensor may carry a single vector, or a whole batch of them under {@code data}. */
    private static List<float[]> extractAllFromTensor(ModelTensor tensor) {
        if (tensor == null) {
            return List.of();
        }
        Number[] data = tensor.getData();
        if (data != null && data.length > 0) {
            float[] vector = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                vector[i] = data[i].floatValue();
            }
            return List.of(vector);
        }

        Map<String, ?> dataAsMap = tensor.getDataAsMap();
        if (dataAsMap == null) {
            return List.of();
        }
        float[] single = toFloatArray(dataAsMap.get(EMBEDDING_FIELD));
        if (single != null) {
            return List.of(single);
        }
        // The batched remote shape: data is a list of per-document objects.
        Object batch = dataAsMap.get(DATA_FIELD);
        if (!(batch instanceof List)) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>();
        for (Object element : (List<?>) batch) {
            if (element instanceof Map) {
                float[] vector = toFloatArray(((Map<?, ?>) element).get(EMBEDDING_FIELD));
                if (vector != null) {
                    vectors.add(vector);
                }
            }
        }
        return vectors;
    }

    private static float[] toFloatArray(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        List<?> values = (List<?>) value;
        if (values.isEmpty()) {
            return null;
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof Number)) {
                return null;
            }
            vector[i] = ((Number) values.get(i)).floatValue();
        }
        return vector;
    }

    /**
     * The node-wide cache, plus a registry of keys being embedded so concurrent callers wait for one
     * batch instead of embedding twice. An in-flight entry older than {@link #ABANDONED_AFTER_NANOS}
     * can be claimed again.
     */
    static class EmbeddingCache {

        static final long ABANDONED_AFTER_NANOS = TimeUnit.MINUTES.toNanos(2);
        /** Rough per-entry overhead beyond the vector itself: the key string and cache bookkeeping. */
        private static final long ENTRY_OVERHEAD_BYTES = 200;

        private final Cache<String, float[]> vectors;
        private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();
        private final LongSupplier nanoClock;

        EmbeddingCache(long maxBytes, TimeValue expireAfterWrite, LongSupplier nanoClock) {
            this.vectors = CacheBuilder
                .<String, float[]>builder()
                .setMaximumWeight(maxBytes)
                .setExpireAfterWrite(expireAfterWrite)
                .weigher((key, vector) -> vector.length * (long) Float.BYTES + ENTRY_OVERHEAD_BYTES)
                .build();
            this.nanoClock = nanoClock;
        }

        /** Sorts the keys into cache hits, keys this caller must embed, and keys another caller is embedding. */
        Claim claim(Iterable<String> keys) {
            Map<String, float[]> hits = new HashMap<>();
            Map<String, InFlight> mine = new LinkedHashMap<>();
            Map<String, CompletableFuture<float[]>> awaited = new HashMap<>();
            for (String key : keys) {
                float[] cached = vectors.get(key);
                if (cached != null) {
                    hits.put(key, cached);
                    continue;
                }
                InFlight fresh = new InFlight(new CompletableFuture<>(), nanoClock.getAsLong());
                InFlight current = inFlight.compute(key, (k, existing) -> {
                    boolean usable = existing != null && fresh.startedNanos - existing.startedNanos < ABANDONED_AFTER_NANOS;
                    return usable ? existing : fresh;
                });
                if (current == fresh) {
                    mine.put(key, fresh);
                } else {
                    awaited.put(key, current.future);
                }
            }
            return new Claim(hits, mine, awaited);
        }

        /** Removes the in-flight entry only if the caller still owns it; a re-claimed entry is left alone. */
        void publish(String key, InFlight own, float[] vector) {
            if (vector != null) {
                vectors.put(key, vector);
            }
            inFlight.remove(key, own);
            own.future.complete(vector);
        }

        /** Releases everyone waiting on the caller's entry with the failure; nothing is cached. */
        void fail(String key, InFlight own, Exception error) {
            inFlight.remove(key, own);
            own.future.completeExceptionally(error);
        }

        int size() {
            return vectors.count();
        }

        int inFlightSize() {
            return inFlight.size();
        }

        static final class InFlight {
            private final CompletableFuture<float[]> future;
            private final long startedNanos;

            private InFlight(CompletableFuture<float[]> future, long startedNanos) {
                this.future = future;
                this.startedNanos = startedNanos;
            }
        }

        @Getter
        @AllArgsConstructor
        static final class Claim {
            private final Map<String, float[]> hits;
            /** Keys this caller must embed, each with the in-flight entry it now owns. */
            private final Map<String, InFlight> mine;
            private final Map<String, CompletableFuture<float[]>> awaited;
        }
    }
}
