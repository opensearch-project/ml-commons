/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
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

import static org.apache.commons.text.StringEscapeUtils.escapeJava;
import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePreprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.gson;

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
            TextDocsInputDataSet inputDataSet = (TextDocsInputDataSet)mlInput.getInputDataset();
            List<String> docs = new ArrayList<>(inputDataSet.getDocs());
            Map<String, Object> params = ImmutableMap.of("text_docs", docs);
            Optional<ConnectorAction> predictAction = connector.findPredictAction();
            if (!predictAction.isPresent()) {
                throw new IllegalArgumentException("no predict action found");
            }
            String preProcessFunction = predictAction.get().getPreProcessFunction();
            if (preProcessFunction == null) {
                throw new IllegalArgumentException("Must provide pre_process_function for predict action to process text docs input.");
            }
            if (preProcessFunction != null && preProcessFunction.contains("${parameters")) {
                StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
                preProcessFunction = substitutor.replace(preProcessFunction);
            }
            Optional<String> processedResponse = executePreprocessFunction(scriptService, preProcessFunction, params);
            if (!processedResponse.isPresent()) {
                throw new IllegalArgumentException("Wrong input");
            }
            Map<String, Object> map = gson.fromJson(processedResponse.get(), Map.class);
            Map<String, Object> parametersMap = (Map<String, Object>) map.get("parameters");
            Map<String, String> processedParameters = new HashMap<>();
            for (String key : parametersMap.keySet()) {
                try {
                    AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                        if (parametersMap.get(key) instanceof String) {
                            processedParameters.put(key, (String) parametersMap.get(key));
                        } else {
                            processedParameters.put(key, gson.toJson(parametersMap.get(key)));
                        }
                        return null;
                    });
                } catch (PrivilegedActionException e) {
                    throw new RuntimeException(e);
                }
            }
            inputData = RemoteInferenceInputDataSet.builder().parameters(processedParameters).build();
        } else if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            inputData = (RemoteInferenceInputDataSet)mlInput.getInputDataset();
        } else {
            throw new IllegalArgumentException("Wrong input type");
        }
        Map<String, String> escapedParameters = new HashMap<>();
        inputData.getParameters().entrySet().forEach(entry -> {
            escapedParameters.put(entry.getKey(), escapeJava(entry.getValue()));
        });
        inputData.setParameters(escapedParameters);
        return inputData;
    }

    public static ModelTensors processOutput(String modelResponse, Connector connector, ScriptService scriptService, Map<String, String> parameters) throws IOException {
        if (modelResponse == null) {
            throw new IllegalArgumentException("model response is null");
        }
        List<ModelTensor> modelTensors = new ArrayList<>();
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        if (!predictAction.isPresent()) {
            throw new IllegalArgumentException("no predict action found");
        }
        String postProcessFunction = predictAction.get().getPostProcessFunction();
        if (postProcessFunction != null && postProcessFunction.contains("${parameters")) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            postProcessFunction = substitutor.replace(postProcessFunction);
        }
        Optional<String> processedResponse = executePostprocessFunction(scriptService, postProcessFunction, modelResponse);

        String response = processedResponse.orElse(modelResponse);
        if (parameters.get(RESPONSE_FILTER_FIELD) == null) {
            connector.parseResponse(response, modelTensors, postProcessFunction != null && processedResponse.isPresent());
        } else {
            Object filteredResponse = JsonPath.parse(response).read(parameters.get(RESPONSE_FILTER_FIELD));
            connector.parseResponse(filteredResponse, modelTensors, postProcessFunction != null && processedResponse.isPresent());
        }

        ModelTensors tensors = ModelTensors.builder().mlModelTensors(modelTensors).build();
        return tensors;
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
