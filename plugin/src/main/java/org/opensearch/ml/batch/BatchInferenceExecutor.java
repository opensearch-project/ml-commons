/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.transport.TransportChannel;

import lombok.extern.log4j.Log4j2;

/**
 * Splits one predict request into size-bounded sub-batches, runs them concurrently, and reassembles
 * the outputs in input order. Every sub-batch is waited for; if any failed, the request fails and
 * reports all of their errors. The request completes once.
 */
@Log4j2
public class BatchInferenceExecutor {

    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;

    public BatchInferenceExecutor(BatchableInputRegistry registry, BatchSplitter splitter) {
        this.registry = registry;
        this.splitter = splitter;
    }

    /**
     * Splits into sub-batches only when there is a config, a handler for the input type, and the request
     * needs more than one sub-batch. A request runs as a single call when the model has no config or
     * already fits within the limits, and fails when the model has a config but the input type has no
     * handler, rather than being sent unsplit.
     */
    public void execute(
        MLInput input,
        BatchInferenceConfig config,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        if (config == null) {
            predictor.asyncPredict(input, listener, channel);
            return;
        }

        BatchableInput handler = registry.get(input);
        if (handler == null) {
            listener
                .onFailure(
                    new IllegalArgumentException(
                        "This model has batch_inference_config set, so its predict requests must be splittable, but input type "
                            + (input == null || input.getInputDataset() == null ? "null" : input.getInputDataset().getInputDataType())
                            + " does not support batch inference. Send a supported input type, or remove "
                            + "batch_inference_config from the model to run requests unsplit."
                    )
                );
            return;
        }

        List<List<BatchItem>> batches;
        try {
            batches = splitter.split(handler.toItems(input), config);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }

        if (batches.size() == 1) {
            predictor.asyncPredict(input, listener, channel);
            return;
        }

        dispatchBatches(input, handler, batches, predictor, channel, listener);
    }

    private void dispatchBatches(
        MLInput input,
        BatchableInput handler,
        List<List<BatchItem>> batches,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        int total = batches.size();
        if (log.isDebugEnabled()) {
            int items = 0;
            for (List<BatchItem> b : batches) {
                items += b.size();
            }
            log.debug("Size-based batching: split {} items into {} sub-batches", items, total);
        }

        AtomicReferenceArray<MLOutput> results = new AtomicReferenceArray<>(total);
        AtomicReferenceArray<Exception> failures = new AtomicReferenceArray<>(total);
        // Decremented once per sub-batch, on success and on failure alike, so exactly one callback sees
        // zero and completes the listener.
        AtomicInteger remaining = new AtomicInteger(total);

        for (int i = 0; i < total; i++) {
            int index = i;
            List<BatchItem> batch = batches.get(i);
            ActionListener<MLTaskResponse> subListener = ActionListener.wrap(response -> {
                try {
                    results.set(index, response.getOutput());
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        complete(failures, results, handler, listener);
                    }
                }
            }, error -> {
                try {
                    failures.set(index, error);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        complete(failures, results, handler, listener);
                    }
                }
            });
            try {
                MLInput subInput = handler.merge(input, batch);
                predictor.asyncPredict(subInput, subListener, channel);
            } catch (Exception dispatchError) {
                // Report a sub-batch that failed before its listener was reachable through the same
                // listener, so every sub-batch still settles exactly once and the counter can reach zero.
                subListener.onFailure(dispatchError);
            }
        }
    }

    private void complete(
        AtomicReferenceArray<Exception> failures,
        AtomicReferenceArray<MLOutput> results,
        BatchableInput handler,
        ActionListener<MLTaskResponse> listener
    ) {
        List<Exception> failed = collectFailures(failures);
        if (failed.isEmpty()) {
            MLOutput merged;
            try {
                merged = handler.combine(toList(results));
            } catch (Exception outputMergeError) {
                listener.onFailure(outputMergeError);
                return;
            }
            listener.onResponse(new MLTaskResponse(merged));
            return;
        }
        Exception failure;
        try {
            failure = asSingleFailure(failed);
        } catch (Exception completionError) {
            failure = completionError;
        }
        listener.onFailure(failure);
    }

    private List<Exception> collectFailures(AtomicReferenceArray<Exception> failures) {
        List<Exception> ordered = new ArrayList<>();
        for (int i = 0; i < failures.length(); i++) {
            Exception failure = failures.get(i);
            if (failure != null) {
                ordered.add(failure);
            }
        }
        return ordered;
    }

    /**
     * A lone failure is reported as-is so its type and status survive, for example a 429 staying a 429.
     * Several failures are summarized in one exception with each original attached as a suppressed
     * exception, so none of them is silently dropped.
     */
    private Exception asSingleFailure(List<Exception> failures) {
        if (failures.size() == 1) {
            return failures.get(0);
        }
        StringBuilder message = new StringBuilder().append(failures.size()).append(" of the sub-batches failed: ");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                message.append("; ");
            }
            message.append(failures.get(i).getMessage());
        }
        Exception combined = new OpenSearchStatusException(message.toString(), combinedStatus(failures));
        for (Exception failure : failures) {
            combined.addSuppressed(failure);
        }
        return combined;
    }

    private RestStatus combinedStatus(List<Exception> failures) {
        RestStatus status = ExceptionsHelper.status(failures.get(0));
        for (Exception failure : failures) {
            RestStatus candidate = ExceptionsHelper.status(failure);
            if (candidate.getStatus() >= 500) {
                status = candidate;
            }
        }
        return status;
    }

    private List<MLOutput> toList(AtomicReferenceArray<MLOutput> array) {
        List<MLOutput> ordered = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            ordered.add(array.get(i));
        }
        return ordered;
    }
}
