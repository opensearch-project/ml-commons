/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.output.MLOutput;

import java.util.Map;

public interface MLExecutable extends Executable {

    MLOutput execute(Input input) throws ExecuteException;

    /**
     * Init model (deploy model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     */
    void initModel(MLModel model, Map<String, Object> params);

    /**
     * Close resources like deployed model.
     */
    void close();
}
