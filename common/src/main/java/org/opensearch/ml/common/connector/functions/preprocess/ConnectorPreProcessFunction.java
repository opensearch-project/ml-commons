/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.ml.common.utils.StringUtils.addDefaultMethod;

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
        List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
        if (docs.size() == 1 && docs.get(0) == null) {
            throw new IllegalArgumentException("No input text or image provided");
        }
    }

    protected String executeScript(ScriptService scriptService, String painlessScript, Map<String, Object> params) {
        Script script = new Script(ScriptType.INLINE, "painless", addDefaultMethod(painlessScript), Collections.emptyMap());
        TemplateScript templateScript = scriptService.compile(script, TemplateScript.CONTEXT).newInstance(params);
        return templateScript.execute();
    }

}
