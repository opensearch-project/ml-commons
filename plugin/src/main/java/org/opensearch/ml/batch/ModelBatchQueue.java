/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Per-model queue that coalesces predict requests from concurrent callers and flushes them together — on
 * a count/byte threshold or after flush_timeout_ms — through the shared BatchSplitter, so every dispatched
 * call still respects the model's size limits. Each result is routed back to the caller that submitted it;
 * retries are left to the connector.
 */
@Log4j2
public class ModelBatchQueue {

    private final String modelId;
    private final BatchInferenceConfig config;
    private final long flushTimeoutMs;
    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;
    private final ThreadPool threadPool;

    private final ConcurrentLinkedQueue<QueueEntry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingEntries = new AtomicInteger();
    private final AtomicLong pendingItems = new AtomicLong();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final AtomicBoolean timerScheduled = new AtomicBoolean(false);

    public ModelBatchQueue(
        String modelId,
        BatchInferenceConfig config,
        BatchableInputRegistry registry,
        BatchSplitter splitter,
        ThreadPool threadPool
    ) {
        this.modelId = modelId;
        this.config = config;
        this.flushTimeoutMs = config.getQueue().getFlushTimeoutMs();
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
    }

    BatchInferenceConfig getConfig() {
        return config;
    }

    public void enqueue(QueueEntry entry) {
        queue.add(entry);
        pendingEntries.incrementAndGet();
        long items = pendingItems.addAndGet(entry.getItemCount());
        long bytes = pendingBytes.addAndGet(entry.getByteSize());

        boolean overCount = config.isItemLimitEnabled() && items >= config.getMaxItemsPerRequest();
        boolean overBytes = config.isByteLimitEnabled() && bytes >= config.getMaxBytesPerRequest();
        if (overCount || overBytes) {
            flush();
        } else {
            scheduleTimer();
        }
    }

    private void scheduleTimer() {
        if (timerScheduled.compareAndSet(false, true)) {
            try {
                // AbstractRunnable, not a lambda: onRejection must clear the flag or timed flush stops forever.
                threadPool.schedule(new AbstractRunnable() {
                    @Override
                    protected void doRun() {
                        flush();
                    }

                    @Override
                    public void onRejection(Exception e) {
                        timerScheduled.set(false);
                        failPending(e);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        timerScheduled.set(false);
                        log.warn("Batch flush timer failed for model {}; will retry on next enqueue", modelId, e);
                    }
                }, TimeValue.timeValueMillis(flushTimeoutMs), REMOTE_PREDICT_THREAD_POOL);
            } catch (Exception e) {
                timerScheduled.set(false);
                log.warn("Failed to schedule batch flush timer for model {}; will retry on next enqueue", modelId, e);
            }
        }
    }

    void flush() {
        List<QueueEntry> batch = drain();
        if (batch == null) {
            return;
        }
        if (!batch.isEmpty()) {
            dispatch(batch);
        }
        if (pendingEntries.get() > 0) {
            scheduleTimer();
        }
    }

    // Returns null if another flush is already draining.
    private List<QueueEntry> drain() {
        if (!flushing.compareAndSet(false, true)) {
            return null;
        }
        try {
            timerScheduled.set(false);
            int toDrain = pendingEntries.getAndSet(0);
            List<QueueEntry> batch = new ArrayList<>(toDrain);
            long drainedItems = 0L;
            long drainedBytes = 0L;
            for (int i = 0; i < toDrain; i++) {
                QueueEntry entry = queue.poll();
                if (entry == null) {
                    break;
                }
                batch.add(entry);
                drainedItems += entry.getItemCount();
                drainedBytes += entry.getByteSize();
            }
            pendingItems.addAndGet(-drainedItems);
            pendingBytes.addAndGet(-drainedBytes);
            return batch;
        } finally {
            flushing.set(false);
        }
    }

    private void failPending(Exception error) {
        List<QueueEntry> batch = drain();
        if (batch != null) {
            failAll(batch, error);
        }
    }

    // Only same-group-key requests may share a call, else one caller's parameters leak onto another's docs.
    private void dispatch(List<QueueEntry> batch) {
        Map<String, List<QueueEntry>> groups = new LinkedHashMap<>();
        for (QueueEntry entry : batch) {
            if (entry.getItems() == null) {
                notifyFailure(entry, unsupportedInputType(entry.getInput()));
                continue;
            }
            if (entry.getGroupKey() == null) {
                notifyFailure(entry, new IllegalStateException("Could not compute a batch group key for the predict request"));
                continue;
            }
            String groupId = entry.getInput().getInputDataset().getInputDataType() + "|" + entry.getGroupKey();
            groups.computeIfAbsent(groupId, k -> new ArrayList<>()).add(entry);
        }
        for (List<QueueEntry> group : groups.values()) {
            try {
                dispatchGroup(group);
            } catch (Exception e) {
                failAll(group, e);
            }
        }
    }

    private IllegalArgumentException unsupportedInputType(MLInput input) {
        Object type = input == null || input.getInputDataset() == null ? "null" : input.getInputDataset().getInputDataType();
        return new IllegalArgumentException(
            "This model has batch_inference_config set, so its predict requests must be splittable, but input type "
                + type
                + " does not support batch inference. Send a supported input type, or remove "
                + "batch_inference_config from the model to run requests unsplit."
        );
    }

    private void dispatchGroup(List<QueueEntry> group) {
        BatchableInput handler = registry.get(group.get(0).getInput());

        List<BatchItem> items;
        List<List<BatchItem>> subBatches;
        try {
            items = decompose(group);
            subBatches = splitter.split(items, config);
        } catch (Exception e) {
            failAll(group, e);
            return;
        }

        if (log.isDebugEnabled()) {
            log
                .debug(
                    "Queue flush for model {}: {} requests, {} items, {} sub-batches",
                    modelId,
                    group.size(),
                    items.size(),
                    subBatches.size()
                );
        }

        new GroupDispatch(handler, group, subBatches).run();
    }

    private final class GroupDispatch {
        private final BatchableInput handler;
        private final List<QueueEntry> group;
        private final List<List<BatchItem>> subBatches;
        private final List<MLOutput[]> results;
        private final AtomicReferenceArray<Exception> entryFailures;
        private final AtomicInteger remaining;

        GroupDispatch(BatchableInput handler, List<QueueEntry> group, List<List<BatchItem>> subBatches) {
            this.handler = handler;
            this.group = group;
            this.subBatches = subBatches;
            this.results = new ArrayList<>(group.size());
            for (QueueEntry entry : group) {
                results.add(new MLOutput[entry.getItemCount()]);
            }
            this.entryFailures = new AtomicReferenceArray<>(group.size());
            this.remaining = new AtomicInteger(subBatches.size());
        }

        void run() {
            for (List<BatchItem> subBatch : subBatches) {
                dispatchSubBatch(subBatch);
            }
        }

        private void dispatchSubBatch(List<BatchItem> subBatch) {
            ActionListener<MLTaskResponse> listener = ActionListener.wrap(response -> {
                try {
                    place(handler, subBatch, response.getOutput(), results);
                } catch (Exception distributeError) {
                    markFailed(subBatch, entryFailures, distributeError);
                } finally {
                    settle();
                }
            }, error -> {
                try {
                    markFailed(subBatch, entryFailures, error);
                } finally {
                    settle();
                }
            });

            try {
                QueueEntry template = group.get(subBatch.get(0).getSourceIndex());
                MLInput merged = handler.merge(template.getInput(), subBatch);
                template.getPredictor().asyncPredict(merged, listener, template.getChannel());
            } catch (Exception dispatchError) {
                listener.onFailure(dispatchError);
            }
        }

        private void settle() {
            if (remaining.decrementAndGet() == 0) {
                finish(group, results, entryFailures, handler);
            }
        }
    }

    private List<BatchItem> decompose(List<QueueEntry> group) {
        List<BatchItem> items = new ArrayList<>();
        for (int entryIdx = 0; entryIdx < group.size(); entryIdx++) {
            List<BatchItem> entryItems = group.get(entryIdx).getItems();
            for (int pos = 0; pos < entryItems.size(); pos++) {
                BatchItem base = entryItems.get(pos);
                items.add(new BatchItem(base.getPayload(), base.getByteSize(), entryIdx, pos));
            }
        }
        return items;
    }

    private void place(BatchableInput handler, List<BatchItem> subBatch, MLOutput output, List<MLOutput[]> results) {
        List<MLOutput> perItem = handler.distribute(output);
        if (perItem.size() != subBatch.size()) {
            throw new IllegalStateException(
                "Model returned "
                    + perItem.size()
                    + " results for a sub-batch of "
                    + subBatch.size()
                    + " items, so results cannot be routed back to their callers"
            );
        }
        for (int i = 0; i < subBatch.size(); i++) {
            BatchItem item = subBatch.get(i);
            results.get(item.getSourceIndex())[item.getPositionInSource()] = perItem.get(i);
        }
    }

    private void markFailed(List<BatchItem> subBatch, AtomicReferenceArray<Exception> entryFailures, Exception error) {
        for (BatchItem item : subBatch) {
            entryFailures.compareAndSet(item.getSourceIndex(), null, error);
        }
    }

    private void finish(
        List<QueueEntry> group,
        List<MLOutput[]> results,
        AtomicReferenceArray<Exception> entryFailures,
        BatchableInput handler
    ) {
        for (int e = 0; e < group.size(); e++) {
            QueueEntry entry = group.get(e);
            Exception failure = entryFailures.get(e);
            if (failure != null) {
                notifyFailure(entry, failure);
                continue;
            }
            MLOutput combined;
            try {
                combined = handler.combine(Arrays.asList(results.get(e)));
            } catch (Exception combineError) {
                notifyFailure(entry, combineError);
                continue;
            }
            notifyResponse(entry, combined);
        }
    }

    private void failAll(List<QueueEntry> group, Exception error) {
        for (QueueEntry entry : group) {
            notifyFailure(entry, error);
        }
    }

    private void notifyResponse(QueueEntry entry, MLOutput output) {
        try {
            entry.getListener().onResponse(new MLTaskResponse(output));
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a response for model {}", modelId, e);
        }
    }

    private void notifyFailure(QueueEntry entry, Exception failure) {
        try {
            entry.getListener().onFailure(failure);
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a failure for model {}", modelId, e);
        }
    }
}
