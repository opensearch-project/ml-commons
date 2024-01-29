/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
public abstract class ConnectorPreProcessFunction implements Function<MLInput, RemoteInferenceInputDataSet> {

    protected boolean returnDirectlyForRemoteInferenceInput;

    @Override
    public RemoteInferenceInputDataSet apply(MLInput mlInput) {
        if (returnDirectlyForRemoteInferenceInput && mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            return (RemoteInferenceInputDataSet)mlInput.getInputDataset();
        } else {
            validate(mlInput);
            return process(mlInput);
        }
    }

    public abstract void validate(MLInput mlInput);

    public abstract RemoteInferenceInputDataSet process(MLInput mlInput);

    List<String> processTextDocs(TextDocsInputDataSet inputDataSet) {
        List<String> docs = new ArrayList<>();
        for (String doc : inputDataSet.getDocs()) {
            if (doc != null) {
                String gsonString = gson.toJson(doc);
                // in 2.9, user will add " before and after string
                // gson.toString(string) will add extra " before after string, so need to remove
                docs.add(gsonString.substring(1, gsonString.length() - 1));
            } else {
                docs.add(null);
            }
        }
        return docs;
    }

    public void validateTextDocsInput(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof TextDocsInputDataSet)) {
            throw new IllegalArgumentException("This pre_process_function can only support TextDocsInputDataSet");
        }
    }
}
