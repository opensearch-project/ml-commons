/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.List;

import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;

/**
 * Per-input-type strategy for turning one request into several and reassembling the results, so that
 * the splitter and the executor never need to know what kind of data they are moving. An input type can
 * only be split if it has an implementation registered in BatchableInputRegistry.
 */
public interface BatchableInput {

    /** Decompose a request into its individual items. */
    List<BatchItem> toItems(MLInput input);

    /** Rebuild a subset of items into one model-legal request, carrying over the source's other state. */
    MLInput merge(MLInput source, List<BatchItem> items);

    /**
     * A key over the request state that merge copies onto every item (parameters, result filter, ...)
     * but not the payload. Requests with equal keys may be coalesced into one model call; requests with
     * differing keys must not, or one caller's parameters would be applied to another's payload.
     */
    String groupKey(MLInput input);

    /** Reassemble ordered sub-batch outputs into a single response. */
    MLOutput combine(List<MLOutput> orderedOutputs);

    /**
     * Split one merged sub-batch output into one output per item, in merge order — the inverse of
     * combine. Used to route each result back to the request it came from.
     */
    List<MLOutput> distribute(MLOutput batchedOutput);

    /**
     * distribute, but only when the model returned exactly one result per item in the sub-batch.
     *
     * A count mismatch means the results can no longer be lined up with the items that produced them, so
     * the request must fail rather than return another item's result. For a split request, reassembling
     * the outputs would concatenate a misaligned sub-batch into the middle of the response and silently
     * shift every result after it. For an unsplit request, a short response can cause the same positional
     * ambiguity within that call.
     *
     * Note for the split path, which needs only the count and discards the returned list: that traverses the output
     * once here and again in combine(), and allocates per-item wrappers that are immediately thrown away. It is
     * deliberate — one implementation means the two paths cannot disagree about what counts as aligned — and cheap
     * today, because distribute() reuses each ModelTensor by reference rather than copying any vector. If it ever
     * becomes expensive, add a counting implementation that does not materialize the list, rather than letting the
     * two paths check the count differently.
     */
    default List<MLOutput> distributeExactly(MLOutput batchedOutput, int itemCount) {
        List<MLOutput> perItem = distribute(batchedOutput);
        if (perItem.size() != itemCount) {
            throw new IllegalStateException(
                "Model returned "
                    + perItem.size()
                    + " results for a sub-batch of "
                    + itemCount
                    + " items, so results cannot be routed back to their callers"
            );
        }
        return perItem;
    }
}
