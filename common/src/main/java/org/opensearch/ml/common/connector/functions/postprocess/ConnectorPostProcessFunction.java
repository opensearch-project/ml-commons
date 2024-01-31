/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.List;
import java.util.function.Function;

public abstract class ConnectorPostProcessFunction<T> implements Function<Object, List<ModelTensor>> {

    @Override
    public List<ModelTensor> apply(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("Can't run post process function as model output is null");
        }
        validate(input);
        return process((T)input);
    }

    public abstract void validate(Object input);

    public abstract List<ModelTensor> process(T input);
}
