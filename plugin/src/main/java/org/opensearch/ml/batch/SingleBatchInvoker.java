/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;

/**
 * Runs inference for one sub-batch, decoupling the batching logic from how a model call is made.
 */
@FunctionalInterface
public interface SingleBatchInvoker {
    void invoke(MLInput subInput, ActionListener<MLOutput> listener);
}
