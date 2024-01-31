/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import java.util.function.Function;

@Log4j2
public abstract class ConnectorPreProcessFunction implements Function<MLInput, RemoteInferenceInputDataSet> {

    protected boolean returnDirectlyForRemoteInferenceInput;

    @Override
    public RemoteInferenceInputDataSet apply(MLInput mlInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Preprocess function input can't be null");
        }
        if (returnDirectlyForRemoteInferenceInput && mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            return (RemoteInferenceInputDataSet)mlInput.getInputDataset();
        } else {
            validate(mlInput);
            return process(mlInput);
        }
    }

    public abstract void validate(MLInput mlInput);

    public abstract RemoteInferenceInputDataSet process(MLInput mlInput);

    public void validateTextDocsInput(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof TextDocsInputDataSet)) {
            throw new IllegalArgumentException("This pre_process_function can only support TextDocsInputDataSet");
        }
    }
}
