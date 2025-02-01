/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import java.util.List;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

public interface ConnectorPostProcessFunction {

    default List<ModelTensor> apply(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("Can't run post process function as model output is null");
        }
        validate(input);
        return process(input);
    }

    default List<ModelTensor> apply(Object input, MLResultDataType dataType) {
        if (input == null) {
            throw new IllegalArgumentException("Can't run post process function as model output is null");
        }
        validate(input);
        return process(input, dataType);
    }

    void validate(Object input);

    List<ModelTensor> process(Object input);

    default List<ModelTensor> process(Object input, MLResultDataType dataType) {
        throw new IllegalArgumentException(
            "The post process function is not expected to run unless your model is a embedding type supported model"
                + " and the response_filter configuration in connector been set to an embedding type path, please check "
                + "connector.post_process.default.embedding for more information"
        );
    }
}
