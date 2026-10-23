/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorThrottlingException;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Per-model queue that coalesces predict requests from concurrent callers and flushes them together — on
 * a count/byte threshold or after flush_timeout_ms — through the shared BatchSplitter, so every dispatched
 * call still respects the model's size limits. A threshold flush sends only full calls; a partial last call goes
 * back to the head of its group with its original deadline, so later requests can fill it. Each result is routed
 * back to the caller that submitted it; retries are left to the connector.
 */
@Log4j2
public class DynamicBatchingQueue {

    private final String modelId;
    private final BatchInferenceConfig config;
    private final long flushTimeoutMs;
    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;
    private final ThreadPool threadPool;
    private final QueueMemoryBudget budget;
    private final Consumer<DynamicBatchingQueue> onIdle;

    private final Object stateLock = new Object();
    // Everything below is guarded by stateLock.
    private final Map<GroupKey, PendingGroup> pending = new LinkedHashMap<>();
    private boolean draining;
    private boolean flushRequested;
    private boolean includePartialRequested;
    private boolean timerScheduled;
    // Used to ignore cancelled timers that have already been dispatched.
    private long timerGeneration;
    private Scheduler.Cancellable scheduledTimer;

    public DynamicBatchingQueue(
        String modelId,
        BatchInferenceConfig config,
        BatchableInputRegistry registry,
        BatchSplitter splitter,
        ThreadPool threadPool,
        QueueMemoryBudget budget,
        Consumer<DynamicBatchingQueue> onIdle
    ) {
        this.modelId = modelId;
        this.config = config;
        this.flushTimeoutMs = config.getDynamicBatching().getFlushTimeoutMs();
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
        this.budget = budget;
        this.onIdle = onIdle;
    }

    String getModelId() {
        return modelId;
    }

    BatchInferenceConfig getConfig() {
        return config;
    }

    boolean isIdle() {
        synchronized (stateLock) {
            // A running round may be about to put a carried tail back, so the queue is not idle until it ends.
            return pending.isEmpty() && !draining;
        }
    }

    /** Returns false when the request was not queued and the caller must run it itself; see TOO_LARGE. */
    public boolean enqueue(QueueEntry entry) {
        EnqueueDecision decision = offer(entry);
        completeEnqueue(entry, decision);
        return decision != EnqueueDecision.TOO_LARGE;
    }

    EnqueueDecision offer(QueueEntry entry) {
        if (notBatchable(entry) != null) {
            return EnqueueDecision.INVALID;
        }
        synchronized (stateLock) {
            // Checked before reserving: a request bigger than the whole node budget can never be admitted, so
            // it is handed back to run unqueued instead of being rejected with a retry that could never succeed.
            if (budget.exceedsCapacity(entry.getRetainedByteSize())) {
                return EnqueueDecision.TOO_LARGE;
            }
            if (!budget.tryReserve(entry.getRetainedByteSize())) {
                return EnqueueDecision.REJECTED;
            }
            entry.setEnqueuedAtNanos(threadPool.preciseRelativeTimeInNanos());
            PendingGroup group = pending.computeIfAbsent(groupKeyOf(entry), key -> new PendingGroup());
            group.add(entry);
            return fillsACall(group.items, group.payloadBytes) ? EnqueueDecision.FLUSH : EnqueueDecision.SCHEDULE_TIMER;
        }
    }

    void completeEnqueue(QueueEntry entry, EnqueueDecision decision) {
        switch (decision) {
            case INVALID:
                notifyUnqueued(entry, notBatchable(entry));
                notifyIdle(); // nothing entered the queue; drop it if this request created an empty one
                break;
            case TOO_LARGE:
                // Left to the caller to run unqueued; nothing was reserved and nothing is pending for it here.
                // Warned rather than debugged: the request still succeeds, but coalescing is silently not happening
                // for it, and if the budget is misconfigured that is true of every request to this model forever.
                log
                    .warn(
                        "Predict request for model {} retains an estimated {} bytes, more than the whole node batch "
                            + "queue budget of {} bytes, so it runs unqueued. Raise {} or {} to let requests this "
                            + "size be coalesced.",
                        modelId,
                        entry.getRetainedByteSize(),
                        budget.getMaxBytes(),
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION.getKey(),
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.getKey()
                    );
                notifyIdle(); // nothing entered the queue; drop it if this request created an empty one
                break;
            case REJECTED:
                notifyUnqueued(
                    entry,
                    new OpenSearchRejectedExecutionException(
                        "Batch inference queue memory budget is exhausted for model " + modelId + "; retry after backoff"
                    )
                );
                notifyIdle(); // nothing entered the queue; drop it if this request created an empty one
                break;
            case FLUSH:
                drain(false);
                break;
            case SCHEDULE_TIMER:
                scheduleTimer();
                break;
        }
    }

    void flush() {
        drain(true);
    }

    private void drain(boolean includePartial) {
        boolean sendPartial = includePartial;
        while (true) {
            Round round = drainRound(sendPartial);
            if (round == null) {
                return;
            }
            if (round.scheduleFailure() != null) {
                failSnapshot(round.scheduleFailure());
            }
            for (Call call : round.calls()) {
                dispatchCall(call);
            }
            if (!round.rerun()) {
                // An empty queue asks the manager to remove it, so the map does not retain a queue per transient model.
                if (!hasPendingEntries()) {
                    notifyIdle();
                }
                return;
            }
            sendPartial = round.rerunIncludePartial();
        }
    }

    private Round drainRound(boolean includePartial) {
        Scheduler.Cancellable timer;
        List<DetachedGroup> detached;
        synchronized (stateLock) {
            if (draining) {
                flushRequested = true;
                includePartialRequested |= includePartial;
                return null;
            }
            detached = detach(includePartial);
            if (detached.isEmpty()) {
                // Arm a timer for work admitted while another round was running.
                return new Round(List.of(), false, false, scheduleTimerLocked());
            }
            draining = true;
            timer = scheduledTimer;
            timerScheduled = false;
            scheduledTimer = null;
        }

        List<Call> calls = new ArrayList<>();
        List<CarriedTail> tails = new ArrayList<>();
        Round round;
        try {
            if (timer != null) {
                timer.cancel();
            }
            for (DetachedGroup group : detached) {
                pack(group, includePartial, calls, tails);
            }
        } finally {
            synchronized (stateLock) {
                for (CarriedTail tail : tails) {
                    pending.computeIfAbsent(tail.key(), key -> new PendingGroup()).prepend(tail);
                }
                boolean rerun = flushRequested || anyGroupFillsACall();
                boolean rerunIncludePartial = includePartialRequested;
                flushRequested = false;
                includePartialRequested = false;
                draining = false;
                // A promised rerun owns pending work if scheduling fails.
                TimerFailure scheduleFailure = scheduleTimerLocked(!rerun);
                round = new Round(calls, rerun, rerunIncludePartial, scheduleFailure);
            }
        }
        return round;
    }

    private List<DetachedGroup> detach(boolean includePartial) {
        List<DetachedGroup> detached = new ArrayList<>();
        Iterator<Map.Entry<GroupKey, PendingGroup>> groups = pending.entrySet().iterator();
        while (groups.hasNext()) {
            Map.Entry<GroupKey, PendingGroup> next = groups.next();
            PendingGroup group = next.getValue();
            if (includePartial || fillsACall(group.items, group.payloadBytes)) {
                detached.add(new DetachedGroup(next.getKey(), group.entries, group.headOffset));
                groups.remove();
            }
        }
        return detached;
    }

    private void pack(DetachedGroup group, boolean includePartial, List<Call> calls, List<CarriedTail> tails) {
        List<QueueEntry> sources = new ArrayList<>(group.entries());
        try {
            List<BatchItem> items = new ArrayList<>();
            for (int source = 0; source < sources.size(); source++) {
                List<BatchItem> entryItems = sources.get(source).getItems();
                for (int pos = source == 0 ? group.headOffset() : 0; pos < entryItems.size(); pos++) {
                    BatchItem item = entryItems.get(pos);
                    items.add(new BatchItem(item.getPayload(), item.getByteSize(), source, pos));
                }
            }
            List<List<BatchItem>> packed = splitter.split(items, config);
            List<BatchItem> last = packed.get(packed.size() - 1);
            long lastBytes = payloadBytes(last);
            CarriedTail tail = null;
            if (!includePartial && !fillsACall(last.size(), lastBytes)) {
                packed.remove(packed.size() - 1);
                BatchItem first = last.get(0);
                List<QueueEntry> tailEntries = new ArrayList<>(sources.subList(first.getSourceIndex(), sources.size()));
                tail = new CarriedTail(group.key(), tailEntries, first.getPositionInSource(), last.size(), lastBytes);
            }
            BatchableInput handler = registry.get(sources.get(0).getInput());
            List<Call> groupCalls = new ArrayList<>(packed.size());
            for (List<BatchItem> callItems : packed) {
                groupCalls.add(Call.of(handler, sources, callItems));
            }
            logFlush(groupCalls, tail);
            calls.addAll(groupCalls);
            if (tail != null) {
                tails.add(tail);
            }
        } catch (Exception e) {
            failAll(group, e); // nothing from this group has been sent yet
        }
    }

    private void logFlush(List<Call> calls, CarriedTail tail) {
        if (!log.isDebugEnabled() || calls.isEmpty()) {
            return;
        }
        long items = 0;
        for (Call call : calls) {
            items += call.items().size();
        }
        List<BatchItem> lastCall = calls.get(calls.size() - 1).items();
        log
            .debug(
                "Queue flush for model {}: {} requests, {} items, {} sub-batches, {} items carried over",
                modelId,
                lastCall.get(lastCall.size() - 1).getSourceIndex() + 1,
                items,
                calls.size(),
                tail == null ? 0 : tail.items()
            );
    }

    private void failAll(DetachedGroup group, Exception error) {
        int source = 0;
        for (QueueEntry entry : group.entries()) {
            int unsent = entry.getItemCount() - (source++ == 0 ? group.headOffset() : 0);
            entry.recordFailure(error);
            if (entry.settle(unsent)) {
                complete(entry);
            }
        }
    }

    private boolean fillsACall(long items, long payloadBytes) {
        return (config.isItemLimitEnabled() && items >= config.getMaxItemsPerRequest())
            || (config.isByteLimitEnabled() && payloadBytes >= config.getMaxBytesPerRequest());
    }

    private boolean anyGroupFillsACall() {
        for (PendingGroup group : pending.values()) {
            if (fillsACall(group.items, group.payloadBytes)) {
                return true;
            }
        }
        return false;
    }

    private static long payloadBytes(List<BatchItem> items) {
        long bytes = 0L;
        for (BatchItem item : items) {
            bytes += item.getByteSize();
        }
        return bytes;
    }

    private void scheduleTimer() {
        TimerFailure failure;
        boolean drainNow;
        synchronized (stateLock) {
            if (draining) {
                return;
            }
            // Revalidate because another enqueue may have made this scheduling decision stale.
            drainNow = anyGroupFillsACall();
            failure = drainNow ? null : scheduleTimerLocked();
        }
        if (drainNow) {
            drain(false);
            return;
        }
        if (failure != null) {
            failSnapshot(failure);
        }
    }

    private TimerFailure scheduleTimerLocked() {
        return scheduleTimerLocked(true);
    }

    private TimerFailure scheduleTimerLocked(boolean failPendingOnError) {
        if (pending.isEmpty() || timerScheduled) {
            return null;
        }
        long generation = ++timerGeneration;
        timerScheduled = true;
        try {
            scheduledTimer = threadPool
                .schedule(new FlushTimer(generation), TimeValue.timeValueMillis(untilOldestDeadlineMs()), REMOTE_PREDICT_THREAD_POOL);
            return null;
        } catch (Exception e) {
            timerScheduled = false;
            scheduledTimer = null;
            if (failPendingOnError) {
                log.warn("Failed to schedule batch flush timer for model {}; failing pending requests", modelId, e);
                return new TimerFailure(e, detach(true));
            }
            log.warn("Failed to schedule batch flush timer for model {}; the requested drain will continue", modelId, e);
            return null;
        }
    }

    private void failSnapshot(TimerFailure failure) {
        for (DetachedGroup group : failure.requests()) {
            failAll(group, failure.error());
        }
        if (!hasPendingEntries()) {
            notifyIdle();
        }
    }

    private long untilOldestDeadlineMs() {
        long oldest = Long.MAX_VALUE;
        for (PendingGroup group : pending.values()) {
            oldest = Math.min(oldest, group.entries.peekFirst().getEnqueuedAtNanos());
        }
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(threadPool.preciseRelativeTimeInNanos() - oldest);
        return Math.max(0L, flushTimeoutMs - waitedMs);
    }

    private boolean claimTimer(long generation) {
        synchronized (stateLock) {
            if (!timerScheduled || generation != timerGeneration) {
                return false;
            }
            timerScheduled = false;
            scheduledTimer = null;
            return true;
        }
    }

    private final class FlushTimer extends AbstractRunnable {
        private final long generation;
        private boolean flushStarted;

        FlushTimer(long generation) {
            this.generation = generation;
        }

        @Override
        protected void doRun() {
            if (claimTimer(generation)) {
                flushStarted = true;
                drain(true);
            }
        }

        @Override
        public void onRejection(Exception e) {
            failIfCurrent(e);
        }

        @Override
        public void onFailure(Exception e) {
            log.warn("Batch flush timer failed for model {}", modelId, e);
            if (!flushStarted) {
                failIfCurrent(e);
            }
        }

        private void failIfCurrent(Exception e) {
            List<DetachedGroup> requests;
            synchronized (stateLock) {
                requests = claimTimer(generation) ? detach(true) : null;
            }
            if (requests != null) {
                failSnapshot(new TimerFailure(e, requests));
            }
        }
    }

    private void dispatchCall(Call call) {
        ActionListener<MLTaskResponse> listener = ActionListener.notifyOnce(ActionListener.wrap(response -> {
            Exception distributeError = null;
            try {
                place(call, response.getOutput());
            } catch (Exception e) {
                distributeError = e;
            }
            settle(call, distributeError);
        }, error -> settle(call, error)));

        try {
            // Any entry in the group shares the same group key (input type + non-payload params), so the
            // sub-batch's first source entry is a valid source for the merge template, predictor and channel.
            QueueEntry firstSourceEntry = call.sources().get(0);
            MLInput merged = call.handler().merge(firstSourceEntry.getInput(), call.items());
            firstSourceEntry.getPredictor().asyncPredict(merged, listener, firstSourceEntry.getChannel());
        } catch (Exception dispatchError) {
            listener.onFailure(dispatchError);
        }
    }

    private void place(Call call, MLOutput output) {
        List<MLOutput> perItem = call.handler().distributeExactly(output, call.items().size());
        for (int i = 0; i < call.items().size(); i++) {
            BatchItem item = call.items().get(i);
            call.sourceOf(item).setResult(item.getPositionInSource(), perItem.get(i));
        }
    }

    private void settle(Call call, Exception error) {
        List<QueueEntry> sources = call.sources();
        int[] settled = new int[sources.size()];
        for (BatchItem item : call.items()) {
            settled[item.getSourceIndex() - call.firstSource()]++;
        }
        if (error != null) {
            Exception reported = perRequestError(error, sources.size());
            for (QueueEntry entry : sources) {
                entry.recordFailure(reported);
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).settle(settled[i])) {
                complete(sources.get(i));
            }
        }
    }

    /**
     * The error to report to each request whose items were in a failed sub-batch.
     *
     * Only an error carrying the provider's raw response body is replaced, and only when the sub-batch merged
     * more than one request - that body describes the merged call rather than any one request in it. Everything
     * else is passed through: an error ml-commons raised itself describes our own handling, not anyone's data,
     * and is what makes a throttle, a guardrail rejection or a sub-batch result-count mismatch diagnosable. A
     * sub-batch holding a single request's items is passed through too, since the message is then entirely about
     * that request - and that is the only shape that exists when coalescing is off.
     */
    private Exception perRequestError(Exception error, int requestCount) {
        if (requestCount <= 1 || !carriesProviderResponseBody(error)) {
            return error;
        }
        log
            .warn(
                "Provider call failed for a sub-batch of model {} that merged {} requests; reporting a generic "
                    + "error to each because the provider message describes the merged call, not one request",
                modelId,
                requestCount,
                error
            );
        String message = "Batch inference failed. This request was merged with other requests for the same model, so the "
            + "provider error is not reported per request; see the cluster logs for details.";
        // Keep the status. The provider's own throttling comes back as 429, and a client or ingest pipeline
        // that retries on 429 but gives up on 500 would otherwise turn a temporary throttle into a
        // permanent failure.
        return error instanceof OpenSearchStatusException statusError
            ? new OpenSearchStatusException(message, statusError.status())
            : new MLException(message);
    }

    /**
     * Whether the exception embeds the provider's raw response body, which is the only part of a failure that
     * can describe another request in the merged call.
     * <p>
     * Decided by the error itself rather than by which listener reported it: the failure listener also receives
     * errors raised before the provider was ever called - model-level and user-level throttling, a guardrail
     * rejection, a model that is not deployed, a payload that would not build. Those must reach the caller
     * unchanged. MLSdkAsyncHttpResponseHandler builds the one error that carries the body, prefixed with
     * {@link org.opensearch.ml.common.CommonValue#REMOTE_SERVICE_ERROR}. RemoteConnectorThrottlingException
     * shares that prefix but its message is a fixed sentence of ours, so it is left alone.
     */
    private static boolean carriesProviderResponseBody(Exception error) {
        return !(error instanceof RemoteConnectorThrottlingException)
            && error.getMessage() != null
            && error.getMessage().startsWith(REMOTE_SERVICE_ERROR);
    }

    private void complete(QueueEntry entry) {
        Exception failure = entry.getFailure();
        if (failure != null) {
            notifyFailure(entry, failure);
            return;
        }
        MLOutput combined;
        try {
            combined = registry.get(entry.getInput()).combine(entry.getResults());
        } catch (Exception combineError) {
            notifyFailure(entry, combineError);
            return;
        }
        notifyResponse(entry, combined);
    }

    private Exception notBatchable(QueueEntry entry) {
        if (entry.getItems() == null) {
            return unsupportedInputType(entry.getInput());
        }
        if (entry.getItems().isEmpty()) {
            return new IllegalArgumentException("Cannot batch a predict request with no input items");
        }
        if (entry.getGroupKey() == null) {
            return new IllegalStateException("Could not compute a batch group key for the predict request");
        }
        return null;
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

    private static GroupKey groupKeyOf(QueueEntry entry) {
        FunctionName callerAlgorithm = entry.getInput().getCallerAlgorithm();
        // Without callerAlgorithm, isolate the request rather than assume compatibility.
        Object callerAlgorithmKey = callerAlgorithm != null ? callerAlgorithm : entry;
        return new GroupKey(entry.getInput().getInputDataset().getInputDataType(), callerAlgorithmKey, entry.getGroupKey());
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

    private void notifyUnqueued(QueueEntry entry, Exception failure) {
        try {
            entry.getListener().onFailure(failure);
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a request the queue did not admit for model {}", modelId, e);
        }
    }

    private void releaseBudget(QueueEntry entry) {
        if (entry.markBudgetReleased()) {
            budget.release(entry.getRetainedByteSize());
        }
    }

    private boolean hasPendingEntries() {
        synchronized (stateLock) {
            return !pending.isEmpty();
        }
    }

    private void notifyIdle() {
        if (onIdle != null) {
            onIdle.accept(this);
        }
    }

    enum EnqueueDecision {
        INVALID,
        /** Bigger than the whole node budget; not queued and not reserved, the caller runs it unqueued. */
        TOO_LARGE,
        REJECTED,
        FLUSH,
        SCHEDULE_TIMER
    }

    private record GroupKey(Object inputType, Object callerAlgorithm, String parametersKey) {
    }

    private record Round(List<Call> calls, boolean rerun, boolean rerunIncludePartial, TimerFailure scheduleFailure) {
    }

    private record TimerFailure(Exception error, List<DetachedGroup> requests) {
    }

    private record DetachedGroup(GroupKey key, ArrayDeque<QueueEntry> entries, int headOffset) {
    }

    private record CarriedTail(GroupKey key, List<QueueEntry> entries, int headOffset, int items, long payloadBytes) {
    }

    private record Call(BatchableInput handler, List<QueueEntry> sources, int firstSource, List<BatchItem> items) {

        static Call of(BatchableInput handler, List<QueueEntry> groupSources, List<BatchItem> items) {
            int first = items.get(0).getSourceIndex();
            int last = items.get(items.size() - 1).getSourceIndex();
            return new Call(handler, List.copyOf(groupSources.subList(first, last + 1)), first, items);
        }

        QueueEntry sourceOf(BatchItem item) {
            return sources.get(item.getSourceIndex() - firstSource);
        }
    }

    private static final class PendingGroup {
        private final ArrayDeque<QueueEntry> entries = new ArrayDeque<>();
        private int headOffset;
        private long items;
        private long payloadBytes;

        void add(QueueEntry entry) {
            entries.addLast(entry);
            items += entry.getItemCount();
            payloadBytes += entry.getPayloadByteSize();
        }

        void prepend(CarriedTail tail) {
            List<QueueEntry> carried = tail.entries();
            for (int i = carried.size() - 1; i >= 0; i--) {
                entries.addFirst(carried.get(i));
            }
            headOffset = tail.headOffset();
            items += tail.items();
            payloadBytes += tail.payloadBytes();
        }
    }
}
