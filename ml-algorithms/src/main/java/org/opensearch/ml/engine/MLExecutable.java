/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;

import java.util.Map;

public interface MLExecutable {

    /**
     * Execute python based ml algorithm with given input data.
     * This interface mostly targeting for those state less algorithm which are built in python and
     * being loaded using DJL to execute with given input data
     * @param input input data
     * @return execution result
     */

    MLOutput execute(Input input) throws Exception;

    /**
     * Init model (load model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     */
    void initModel(MLModel model, Map<String, Object> params);

    /**
     * Close resources like loaded model.
     */
    void close();
}
