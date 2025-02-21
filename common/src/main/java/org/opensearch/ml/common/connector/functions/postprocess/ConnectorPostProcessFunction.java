/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import java.util.List;
import java.util.function.BiFunction;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

public abstract class ConnectorPostProcessFunction<T> implements BiFunction<Object, MLResultDataType, List<ModelTensor>> {

    @Override
    public List<ModelTensor> apply(Object input, MLResultDataType dataType) {
        if (input == null) {
            throw new IllegalArgumentException("Can't run post process function as model output is null");
        }
        validate(input);
        return process((T) input, dataType);
    }

    public abstract void validate(Object input);

    public abstract List<ModelTensor> process(T input, MLResultDataType dataType);
}
