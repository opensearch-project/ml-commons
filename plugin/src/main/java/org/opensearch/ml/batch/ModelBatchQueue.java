/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.threadpool.Scheduler;
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
    private final QueueMemoryBudget budget;

    private final Object stateLock = new Object();
    private final ArrayDeque<QueueEntry> queue = new ArrayDeque<>();
    private Totals totals = Totals.ZERO;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private boolean timerScheduled;
    private Scheduler.Cancellable scheduledTimer;
    private volatile long lastUsedNanos = System.nanoTime();

    public ModelBatchQueue(
        String modelId,
        BatchInferenceConfig config,
        BatchableInputRegistry registry,
        BatchSplitter splitter,
        ThreadPool threadPool,
        QueueMemoryBudget budget
    ) {
        this.modelId = modelId;
        this.config = config;
        this.flushTimeoutMs = config.getQueue().getFlushTimeoutMs();
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
        this.budget = budget;
    }

    BatchInferenceConfig getConfig() {
        return config;
    }

    long getLastUsedNanos() {
        return lastUsedNanos;
    }

    boolean isIdle() {
        synchronized (stateLock) {
            return totals.entries() == 0 && !draining.get();
        }
    }

    public void enqueue(QueueEntry entry) {
        completeEnqueue(entry, offer(entry));
    }

    EnqueueDecision offer(QueueEntry entry) {
        synchronized (stateLock) {
            if (!budget.tryReserve(entry.getRetainedByteSize())) {
                return EnqueueDecision.REJECTED;
            }
            lastUsedNanos = System.nanoTime();
            queue.addLast(entry);
            totals = totals.plus(entry);

            boolean overCount = config.isItemLimitEnabled() && totals.items() >= config.getMaxItemsPerRequest();
            boolean overBytes = config.isByteLimitEnabled() && totals.payloadBytes() >= config.getMaxBytesPerRequest();
            return overCount || overBytes ? EnqueueDecision.FLUSH : EnqueueDecision.SCHEDULE_TIMER;
        }
    }

    void completeEnqueue(QueueEntry entry, EnqueueDecision decision) {
        switch (decision) {
            case REJECTED:
                notifyRejected(entry);
                break;
            case FLUSH:
                flush();
                break;
            case SCHEDULE_TIMER:
                scheduleTimer();
                break;
        }
    }

    private void scheduleTimer() {
        Exception scheduleFailure = null;
        synchronized (stateLock) {
            if (totals.entries() == 0 || timerScheduled) {
                return;
            }
            timerScheduled = true;
            try {
                scheduledTimer = threadPool.schedule(new AbstractRunnable() {
                    @Override
                    protected void doRun() {
                        flush();
                    }

                    @Override
                    public void onRejection(Exception e) {
                        clearTimerState();
                        failPending(e);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        clearTimerState();
                        log.warn("Batch flush timer failed for model {}; failing pending requests", modelId, e);
                        failPending(e);
                    }
                }, TimeValue.timeValueMillis(flushTimeoutMs), REMOTE_PREDICT_THREAD_POOL);
            } catch (Exception e) {
                timerScheduled = false;
                scheduledTimer = null;
                scheduleFailure = e;
            }
        }
        if (scheduleFailure != null) {
            log.warn("Failed to schedule batch flush timer for model {}; failing pending requests", modelId, scheduleFailure);
            failPending(scheduleFailure);
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
        if (hasPendingEntries()) {
            scheduleTimer();
        }
    }

    private List<QueueEntry> drain() {
        if (!draining.compareAndSet(false, true)) {
            return null;
        }
        try {
            Scheduler.Cancellable timer;
            List<QueueEntry> batch;
            synchronized (stateLock) {
                timerScheduled = false;
                timer = scheduledTimer;
                scheduledTimer = null;
                batch = new ArrayList<>(queue);
                queue.clear();
                totals = Totals.ZERO;
            }
            if (timer != null) {
                timer.cancel();
            }
            return batch;
        } finally {
            draining.set(false);
        }
    }

    private void failPending(Exception error) {
        List<QueueEntry> batch = drain();
        if (batch != null) {
            failAll(batch, error);
        }
    }

    private void dispatch(List<QueueEntry> batch) {
        Map<GroupKey, List<QueueEntry>> groups = new LinkedHashMap<>();
        for (QueueEntry entry : batch) {
            if (entry.getItems() == null) {
                notifyFailure(entry, unsupportedInputType(entry.getInput()));
                continue;
            }
            if (entry.getGroupKey() == null) {
                notifyFailure(entry, new IllegalStateException("Could not compute a batch group key for the predict request"));
                continue;
            }
            GroupKey groupId = new GroupKey(entry.getInput().getInputDataset().getInputDataType(), entry.getGroupKey());
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
                // Any entry in the group shares the same group key (input type + non-payload params), so the
                // sub-batch's first source entry is a valid source for the merge template, predictor and channel.
                QueueEntry firstSourceEntry = group.get(subBatch.get(0).getSourceIndex());
                MLInput merged = handler.merge(firstSourceEntry.getInput(), subBatch);
                firstSourceEntry.getPredictor().asyncPredict(merged, listener, firstSourceEntry.getChannel());
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

    private void clearTimerState() {
        synchronized (stateLock) {
            timerScheduled = false;
            scheduledTimer = null;
        }
    }

    private void notifyResponse(QueueEntry entry, MLOutput output) {
        try {
            entry.getListener().onResponse(new MLTaskResponse(output));
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a response for model {}", modelId, e);
        } finally {
            releaseBudget(entry);
        }
    }

    private void notifyFailure(QueueEntry entry, Exception failure) {
        try {
            entry.getListener().onFailure(failure);
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a failure for model {}", modelId, e);
        } finally {
            releaseBudget(entry);
        }
    }

    private void notifyRejected(QueueEntry entry) {
        try {
            entry
                .getListener()
                .onFailure(
                    new OpenSearchRejectedExecutionException(
                        "Batch inference queue memory budget is exhausted for model " + modelId + "; retry after backoff"
                    )
                );
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a rejection for model {}", modelId, e);
        }
    }

    private void releaseBudget(QueueEntry entry) {
        if (entry.markBudgetReleased()) {
            budget.release(entry.getRetainedByteSize());
        }
    }

    private boolean hasPendingEntries() {
        synchronized (stateLock) {
            return totals.entries() > 0;
        }
    }

    enum EnqueueDecision {
        REJECTED,
        FLUSH,
        SCHEDULE_TIMER
    }

    private record GroupKey(Object inputType, String parametersKey) {
    }

    private record Totals(int entries, long items, long payloadBytes) {

        static final Totals ZERO = new Totals(0, 0L, 0L);

        Totals plus(QueueEntry entry) {
            return new Totals(entries + 1, items + entry.getItemCount(), payloadBytes + entry.getPayloadByteSize());
        }
    }
}
