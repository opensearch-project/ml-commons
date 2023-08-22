/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.GsonUtil;
import org.opensearch.script.ScriptService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.engine.utils.ScriptUtils.executeBuildInPostProcessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostProcessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePreprocessFunction;

@Log4j2
public class ConnectorUtils {

    private static final Aws4Signer signer;
    static {
        signer = Aws4Signer.create();
    }

    public static RemoteInferenceInputDataSet processInput(MLInput mlInput, Connector connector, Map<String, String> parameters, ScriptService scriptService) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Input is null");
        }
        RemoteInferenceInputDataSet inputData;
        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            inputData = processTextDocsInput((TextDocsInputDataSet) mlInput.getInputDataset(), connector, parameters, scriptService);
        } else if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            inputData = (RemoteInferenceInputDataSet)mlInput.getInputDataset();
        } else {
            throw new IllegalArgumentException("Wrong input type");
        }
        if (inputData.getParameters() != null) {
            Map<String, String> newParameters = new HashMap<>();
            inputData.getParameters().forEach((key, value) -> {
                if (value == null) {
                    newParameters.put(key, null);
                } else if (org.opensearch.ml.common.utils.StringUtils.isJson(value)) {
                    // no need to escape if it's already valid json
                    newParameters.put(key, value);
                } else {
                    newParameters.put(key, escapeJson(value));
                }
            });
            inputData.setParameters(newParameters);
        }
        return inputData;
    }
    private static RemoteInferenceInputDataSet processTextDocsInput(TextDocsInputDataSet inputDataSet, Connector connector, Map<String, String> parameters, ScriptService scriptService) {
        List<String> docs = new ArrayList<>(inputDataSet.getDocs());
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        if (predictAction.isEmpty()) {
            throw new IllegalArgumentException("no predict action found");
        }
        String preProcessFunction = predictAction.get().getPreProcessFunction();
        if (preProcessFunction == null) {
            throw new IllegalArgumentException("Must provide pre_process_function for predict action to process text docs input.");
        }
        if (MLPreProcessFunction.contains(preProcessFunction)) {
            Map<String, Object> buildInFunctionResult = MLPreProcessFunction.get(preProcessFunction).apply(docs);
            return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(buildInFunctionResult)).build();
        } else {
            if (preProcessFunction.contains("${parameters")) {
                StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                preProcessFunction = substitutor.replace(preProcessFunction);
            }
            Optional<String> processedInput = executePreprocessFunction(scriptService, preProcessFunction, docs);
            if (processedInput.isEmpty()) {
                throw new IllegalArgumentException("Wrong input");
            }
            Map<String, Object> map = GsonUtil.fromJson(processedInput.get(), Map.class);
            return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(map)).build();
        }
    }

    private static Map<String, String> convertScriptStringToJsonString(Map<String, Object> processedInput) {
        Map<String, String> parameterStringMap = new HashMap<>();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Map<String, Object> parametersMap = (Map<String, Object>) processedInput.get("parameters");
                for (String key : parametersMap.keySet()) {
                    if (parametersMap.get(key) instanceof String) {
                        parameterStringMap.put(key, (String) parametersMap.get(key));
                    } else {
                        parameterStringMap.put(key, GsonUtil.toJson(parametersMap.get(key)));
                    }
                }
                return null;
            });
        } catch (PrivilegedActionException e) {
            log.error("Error processing parameters", e);
            throw new RuntimeException(e);
        }
        return parameterStringMap;
    }

    public static ModelTensors processOutput(String modelResponse, Connector connector, ScriptService scriptService, Map<String, String> parameters) throws IOException {
        if (modelResponse == null) {
            throw new IllegalArgumentException("model response is null");
        }
        List<ModelTensor> modelTensors = new ArrayList<>();
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        if (predictAction.isEmpty()) {
            throw new IllegalArgumentException("no predict action found");
        }
        ConnectorAction connectorAction = predictAction.get();
        String postProcessFunction = connectorAction.getPostProcessFunction();
        if (postProcessFunction != null && postProcessFunction.contains("${parameters")) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            postProcessFunction = substitutor.replace(postProcessFunction);
        }

        String responseFilter = parameters.get(RESPONSE_FILTER_FIELD);
        if (MLPostProcessFunction.contains(postProcessFunction)) {
            // in this case, we can use jsonpath to build a List<List<Float>> result from model response.
            if (StringUtils.isBlank(responseFilter)) responseFilter = MLPostProcessFunction.getResponseFilter(postProcessFunction);
            List<List<Float>> vectors = JsonPath.read(modelResponse, responseFilter);
            List<ModelTensor> processedResponse = executeBuildInPostProcessFunction(vectors, MLPostProcessFunction.get(postProcessFunction));
            return ModelTensors.builder().mlModelTensors(processedResponse).build();
        }

        // execute user defined painless script.
        Optional<String> processedResponse = executePostProcessFunction(scriptService, postProcessFunction, modelResponse);
        String response = processedResponse.orElse(modelResponse);
        boolean scriptReturnModelTensor = postProcessFunction != null && processedResponse.isPresent();
        if (responseFilter == null) {
            connector.parseResponse(response, modelTensors, scriptReturnModelTensor);
        } else {
            Object filteredResponse = JsonPath.parse(response).read(parameters.get(RESPONSE_FILTER_FIELD));
            connector.parseResponse(filteredResponse, modelTensors, scriptReturnModelTensor);
        }
        return ModelTensors.builder().mlModelTensors(modelTensors).build();
    }

    public static SdkHttpFullRequest signRequest(SdkHttpFullRequest request, String accessKey, String secretKey, String sessionToken, String signingName, String region) {
        AwsCredentials credentials = sessionToken == null ? AwsBasicCredentials.create(accessKey, secretKey) : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);

        Aws4SignerParams params = Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingName(signingName)
                .signingRegion(Region.of(region))
                .build();

        return signer.sign(request, params);
    }
}
