/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

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
     * Runs the request against a predictor, adapting it to the invoker this executor works with, so
     * callers do not have to wire up the per-sub-batch call themselves.
     */
    public void execute(
        MLInput input,
        BatchInferenceConfig config,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        execute(
            input,
            config,
            (subInput, subListener) -> predictor
                .asyncPredict(
                    subInput,
                    ActionListener.wrap(resp -> subListener.onResponse(resp.getOutput()), subListener::onFailure),
                    channel
                ),
            ActionListener.wrap(merged -> listener.onResponse(new MLTaskResponse(merged)), listener::onFailure)
        );
    }

    /**
     * Splits into sub-batches only when there is a config, a handler for the input type, and the request
     * needs more than one sub-batch. A request runs as a single call when the model has no config or
     * already fits within the limits, and fails when the model has a config but the input type has no
     * handler, rather than being sent unsplit.
     */
    public void execute(MLInput input, BatchInferenceConfig config, SingleBatchInvoker invoker, ActionListener<MLOutput> listener) {
        if (config == null) {
            invoker.invoke(input, listener);
            return;
        }

        final BatchableInput handler = registry.get(input);
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

        final List<List<BatchItem>> batches;
        try {
            batches = splitter.split(handler.toItems(input), config);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }

        dispatchBatches(input, handler, batches, invoker, listener);
    }

    private void dispatchBatches(
        MLInput input,
        BatchableInput handler,
        List<List<BatchItem>> batches,
        SingleBatchInvoker invoker,
        ActionListener<MLOutput> listener
    ) {
        final int total = batches.size();
        if (total == 1) {
            // Already within the limits, so send the original request untouched.
            invoker.invoke(input, listener);
            return;
        }

        if (log.isDebugEnabled()) {
            int items = 0;
            for (List<BatchItem> b : batches) {
                items += b.size();
            }
            log.debug("Size-based batching: split {} items into {} sub-batches", items, total);
        }

        final AtomicReferenceArray<MLOutput> results = new AtomicReferenceArray<>(total);
        final List<Exception> failures = Collections.synchronizedList(new ArrayList<>());
        // Decremented once per sub-batch, on success and on failure alike, so exactly one callback sees
        // zero and completes the listener.
        final AtomicInteger remaining = new AtomicInteger(total);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final List<BatchItem> batch = batches.get(i);
            final ActionListener<MLOutput> subListener = ActionListener.wrap(output -> {
                results.set(index, output);
                if (remaining.decrementAndGet() == 0) {
                    complete(failures, results, handler, listener);
                }
            }, error -> {
                failures.add(error);
                if (remaining.decrementAndGet() == 0) {
                    complete(failures, results, handler, listener);
                }
            });
            try {
                invoker.invoke(handler.merge(input, batch), subListener);
            } catch (Exception dispatchError) {
                // Report a sub-batch that failed before its listener was reachable through the same
                // listener, so every sub-batch still settles exactly once and the counter can reach zero.
                subListener.onFailure(dispatchError);
            }
        }
    }

    private void complete(
        List<Exception> failures,
        AtomicReferenceArray<MLOutput> results,
        BatchableInput handler,
        ActionListener<MLOutput> listener
    ) {
        if (failures.isEmpty()) {
            try {
                listener.onResponse(handler.combine(toList(results)));
            } catch (Exception outputMergeError) {
                listener.onFailure(outputMergeError);
            }
            return;
        }
        listener.onFailure(asSingleFailure(failures));
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
        Exception combined = new OpenSearchStatusException(message.toString(), RestStatus.INTERNAL_SERVER_ERROR);
        for (Exception failure : failures) {
            combined.addSuppressed(failure);
        }
        return combined;
    }

    private List<MLOutput> toList(AtomicReferenceArray<MLOutput> array) {
        List<MLOutput> ordered = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            ordered.add(array.get(i));
        }
        return ordered;
    }
}
