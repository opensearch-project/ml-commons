/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import java.util.Map;

import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

/**
 * The PreProcessFunction interface defines methods for preprocessing {@link MLInput} data
 * before it is used for inference. It includes methods to apply preprocessing with or without
 * additional parameters and to validate the input data.
 */
public interface PreProcessFunction {

    RemoteInferenceInputDataSet apply(Map<String, String> connectorParams, MLInput mlInput);

    RemoteInferenceInputDataSet apply(MLInput mlInput);

    /**
     * The default behavior of this method is to invoke process method with only the MLInput parameter, when the process
     * needs more parameters from the connector parameters, the concrete implementation should override this method.
     * @param connectorParams
     * @param mlInput
     * @return
     */
    default RemoteInferenceInputDataSet process(Map<String, String> connectorParams, MLInput mlInput) {
        return process(mlInput);
    }

    RemoteInferenceInputDataSet process(MLInput mlInput);

    void validate(MLInput mlInput);
}
