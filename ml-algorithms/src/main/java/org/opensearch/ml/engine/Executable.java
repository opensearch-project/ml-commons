/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.exception.ExecuteException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.output.Output;
import org.opensearch.transport.TransportChannel;

public interface Executable {

    /**
     * Execute algorithm with given input data (non-streaming).
     * @param input input data
     * @param listener action listener
     */
    default void execute(Input input, ActionListener<Output> listener) throws ExecuteException {
        execute(input, listener, null);
    }

    /**
     * Execute algorithm with given input data (streaming).
     * @param input input data
     * @param listener action listener
     * @param channel transport channel
     */
    default void execute(Input input, ActionListener<Output> listener, TransportChannel channel) {}
}
