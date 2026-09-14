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
}
