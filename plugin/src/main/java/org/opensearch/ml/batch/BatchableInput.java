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

    /** Reassemble ordered sub-batch outputs into a single response. */
    MLOutput combine(List<MLOutput> orderedOutputs);
}
