/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.Output;

public interface Executable {

    /**
     * Execute algorithm with given input data.
     * @param input input data
     * @return execution result
     */
    Output execute(Input input) throws ExecuteException;
}
