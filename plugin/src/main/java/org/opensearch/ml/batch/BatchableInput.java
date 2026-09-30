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

    /** The number of results in a sub-batch output, without materializing one output per item. */
    int resultCount(MLOutput batchedOutput);

    /**
     * Fails unless the model returned exactly one result per item in the sub-batch.
     *
     * A count mismatch means the results can no longer be lined up with the items that produced them, so
     * the request must fail rather than return another item's result. For a split request, reassembling
     * the outputs would concatenate a misaligned sub-batch into the middle of the response and silently
     * shift every result after it. For an unsplit request, a short response can cause the same positional
     * ambiguity within that call.
     */
    default void ensureResultCount(MLOutput batchedOutput, int itemCount) {
        validateResultCount(resultCount(batchedOutput), itemCount);
    }

    /** distribute, but only when the model returned exactly one result per item in the sub-batch. */
    default List<MLOutput> distributeExactly(MLOutput batchedOutput, int itemCount) {
        List<MLOutput> perItem = distribute(batchedOutput);
        validateResultCount(perItem.size(), itemCount);
        return perItem;
    }

    private static void validateResultCount(int resultCount, int itemCount) {
        if (resultCount != itemCount) {
            throw new IllegalStateException(
                "Model returned "
                    + resultCount
                    + " results for a sub-batch of "
                    + itemCount
                    + " items, so results cannot be routed back to their callers"
            );
        }
    }
}
