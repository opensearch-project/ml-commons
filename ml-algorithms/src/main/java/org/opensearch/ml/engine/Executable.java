/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.exception.ExecuteException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.output.Output;

public interface Executable {

    /**
     * Execute algorithm with given input data.
     * @param input input data
     * @return execution result
     */
    default void execute(Input input, ActionListener<Output> listener) throws ExecuteException {
        execute(input, null, listener);
    }

    /**
     * Execute algorithm with given input data.
     * @param input input data
     * @return execution result
     */
    void execute(Input input, String tenantId, ActionListener<Output> listener) throws ExecuteException;
}
