/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.ml.common.utils.StringUtils.addDefaultMethod;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;

import lombok.extern.log4j.Log4j2;

/**
 * This abstract class represents a pre-processing function for a connector.
 * It takes an instance of {@link MLInput} as input and returns an instance of {@link RemoteInferenceInputDataSet}.
 * The input data is expected to be of type {@link MLInput}, and the pre-processing function can be customized by implementing the {@link #validate(MLInput)} and {@link #process(MLInput)} methods.
 * If the input data is already of type {@link RemoteInferenceInputDataSet}, it can be returned directly by setting the {@link #returnDirectlyForRemoteInferenceInput} flag to true.
 */
@Log4j2
public abstract class ConnectorPreProcessFunction implements PreProcessFunction {

    /**
     * This is a flag that can be used to determine if the pre-process function should return the input directly for RemoteInferenceInputDataSet.
     * If this is true and the input is already of type RemoteInferenceInputDataSet, it will be returned directly, otherwise it will be processed.
     */
    protected boolean returnDirectlyForRemoteInferenceInput;

    /**
     * Applies the pre-processing function to the given MLInput object and returns the resulting RemoteInferenceInputDataSet.
     *
     * @param connectorParams the connector parameters: including parameters defined in the connector and the parameters from request.
     *                        refer to RemoteConnectorExecutor.preparePayloadAndInvoke for details.
     * @param  mlInput  the MLInput object to be processed
     * @return RemoteInferenceInputDataSet resulting from the pre-processing function
     * @throws IllegalArgumentException if the input MLInput object is null
     */
    @Override
    public RemoteInferenceInputDataSet apply(Map<String, String> connectorParams, MLInput mlInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Preprocess function input can't be null");
        }
        if (returnDirectlyForRemoteInferenceInput && mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            return (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        } else {
            validate(mlInput);
            if (connectorParams != null) {
                return process(connectorParams, mlInput);
            } else {
                return process(mlInput);
            }
        }
    }

    /**
     * Applies the pre-processing function to the given MLInput object and returns the resulting RemoteInferenceInputDataSet.
     *
     * @param  mlInput  the MLInput object to be processed
     * @return          the RemoteInferenceInputDataSet resulting from the pre-processing function
     * @throws IllegalArgumentException if the input MLInput object is null
     */
    @Override
    public RemoteInferenceInputDataSet apply(MLInput mlInput) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Preprocess function input can't be null");
        }
        if (returnDirectlyForRemoteInferenceInput && mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            return (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        } else {
            validate(mlInput);
            return process(mlInput);
        }
    }

    /**
     * Validates the input of a pre-process function for text documents.
     *
     * @param  mlInput  the input data to be validated
     * @throws IllegalArgumentException  if the input dataset is not an instance of TextDocsInputDataSet
     *                                   or if there is no input text or image provided
     */
    public void validateTextDocsInput(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof TextDocsInputDataSet)) {
            log
                .error(
                    String
                        .format(
                            Locale.ROOT,
                            "This pre_process_function can only support TextDocsInputDataSet, actual input type is: %s",
                            mlInput.getInputDataset().getClass().getName()
                        )
                );
            throw new IllegalArgumentException(
                "This pre_process_function can only support TextDocsInputDataSet which including a list of string with key 'text_docs'"
            );
        }
    }

    protected String executeScript(ScriptService scriptService, String painlessScript, Map<String, Object> params) {
        Script script = new Script(ScriptType.INLINE, "painless", addDefaultMethod(painlessScript), Collections.emptyMap());
        TemplateScript templateScript = scriptService.compile(script, TemplateScript.CONTEXT).newInstance(params);
        return templateScript.execute();
    }

}
