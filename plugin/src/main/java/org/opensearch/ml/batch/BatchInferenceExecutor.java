/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Splits one predict request into size-bounded sub-batches, runs them concurrently, and reassembles
 * the outputs in input order. Throttling (429) and unavailable (503) errors are retried per sub-batch
 * with exponential backoff; any other error fails the whole request. The request completes once.
 */
@Log4j2
public class BatchInferenceExecutor {

    static final int MAX_RETRIES = 3;
    static final long[] BACKOFF_MS = { 1000L, 2000L, 4000L };

    private final BatchableInputRegistry registry;
    private final SizeBasedBatchSplitter splitter;
    private final ThreadPool threadPool;
    private final String scheduleExecutorName;

    public BatchInferenceExecutor(
        BatchableInputRegistry registry,
        SizeBasedBatchSplitter splitter,
        ThreadPool threadPool,
        String scheduleExecutorName
    ) {
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
        this.scheduleExecutorName = scheduleExecutorName;
    }

    /**
     * True only when there is a config, a handler for the input type, and the request needs more than
     * one sub-batch. Otherwise the caller should invoke the model directly.
     */
    public boolean shouldBatch(MLInput input, BatchInferenceConfig config) {
        if (config == null) {
            return false;
        }
        BatchableInput handler = registry.get(input);
        if (handler == null) {
            return false;
        }
        return splitter.split(handler.toItems(input), config).size() > 1;
    }

    public void execute(MLInput input, BatchInferenceConfig config, SingleBatchInvoker invoker, ActionListener<MLOutput> listener) {
        final BatchableInput handler = registry.get(input);
        if (handler == null) {
            invoker.invoke(input, listener);
            return;
        }

        final List<List<BatchItem>> batches;
        try {
            batches = splitter.split(handler.toItems(input), config);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }

        if (batches.size() == 1) {
            invoker.invoke(input, listener);
            return;
        }

        final int total = batches.size();
        if (log.isDebugEnabled()) {
            int items = 0;
            for (List<BatchItem> b : batches) {
                items += b.size();
            }
            log.debug("Size-based batching: split {} items into {} sub-batches", items, total);
        }
        final AtomicReferenceArray<MLOutput> results = new AtomicReferenceArray<>(total);
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicBoolean completed = new AtomicBoolean(false);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final MLInput subInput = handler.merge(input, batches.get(i));
            final ActionListener<MLOutput> subListener = ActionListener.wrap(output -> {
                results.set(index, output);
                if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                    try {
                        listener.onResponse(handler.combine(toList(results)));
                    } catch (Exception combineError) {
                        listener.onFailure(combineError);
                    }
                }
            }, error -> {
                if (completed.compareAndSet(false, true)) {
                    listener.onFailure(error);
                }
            });
            invokeWithRetry(invoker, subInput, 0, subListener);
        }
    }

    private List<MLOutput> toList(AtomicReferenceArray<MLOutput> array) {
        List<MLOutput> ordered = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            ordered.add(array.get(i));
        }
        return ordered;
    }

    private void invokeWithRetry(SingleBatchInvoker invoker, MLInput subInput, int attempt, ActionListener<MLOutput> listener) {
        invoker.invoke(subInput, ActionListener.wrap(listener::onResponse, error -> {
            if (attempt < MAX_RETRIES && isRetryable(error)) {
                long backoff = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
                try {
                    threadPool
                        .schedule(
                            () -> invokeWithRetry(invoker, subInput, attempt + 1, listener),
                            TimeValue.timeValueMillis(backoff),
                            scheduleExecutorName
                        );
                } catch (Exception scheduleError) {
                    listener.onFailure(scheduleError);
                }
            } else {
                listener.onFailure(error);
            }
        }));
    }

    static boolean isRetryable(Exception e) {
        if (e instanceof OpenSearchStatusException) {
            RestStatus status = ((OpenSearchStatusException) e).status();
            return status == RestStatus.TOO_MANY_REQUESTS || status == RestStatus.SERVICE_UNAVAILABLE;
        }
        return false;
    }
}
