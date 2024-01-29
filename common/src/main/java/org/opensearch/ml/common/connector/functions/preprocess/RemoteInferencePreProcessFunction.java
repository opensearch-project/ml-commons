/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;
import static org.opensearch.ml.common.utils.StringUtils.gson;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RemoteInferencePreProcessFunction extends ConnectorPreProcessFunction {

    ScriptService scriptService;
    String preProcessFunction;

    @Builder
    public RemoteInferencePreProcessFunction(ScriptService scriptService, String preProcessFunction) {
        this.returnDirectlyForRemoteInferenceInput = false;
        this.scriptService = scriptService;
        this.preProcessFunction = preProcessFunction;
    }

    @Override
    public void validate(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet)) {
            throw new IllegalArgumentException("This pre_process_function can only support RemoteInferenceInputDataSet");
        }
    }

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.putAll(((RemoteInferenceInputDataSet)mlInput.getInputDataset()).getParameters());
        String processedInput = executeScript(scriptService, preProcessFunction, inputParams);
        if (processedInput == null) {
            throw new IllegalArgumentException("Input is null after processed by preprocess function");
        }
        Map<String, Object> map = gson.fromJson(processedInput, Map.class);
        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(map)).build();
    }

    String executeScript(ScriptService scriptService, String painlessScript, Map<String, Object> params) {
        Script script = new Script(ScriptType.INLINE, "painless", painlessScript, Collections.emptyMap());
        TemplateScript templateScript = scriptService.compile(script, TemplateScript.CONTEXT).newInstance(params);
        return templateScript.execute();
    }
}
