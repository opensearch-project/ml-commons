/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.Output;

public interface Executable {

    /**
     * Execute algorithm with given input data.
     * @param input input data
     * @return execution result
     */
    Output execute(Input input);

}
