/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.JsonPath;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePreprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.gson;

public class ConnectorUtils {

    private static final Aws4Signer signer;
    static {
        signer = Aws4Signer.create();
    }

    public static RemoteInferenceInputDataSet processInput(MLInput mlInput, Connector connector, ScriptService scriptService) {
        RemoteInferenceInputDataSet inputData;
        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            TextDocsInputDataSet inputDataSet = (TextDocsInputDataSet)mlInput.getInputDataset();
            Map<String, Object> params = ImmutableMap.of("text_docs", inputDataSet.getDocs());
            String preProcessFunction = connector.getPreProcessFunction();
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
                            processedParameters.put(key, (String)parametersMap.get(key));
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
        return inputData;
    }

    public static ModelTensors processOutput(String modelResponse, Connector connector, ScriptService scriptService, Map<String, String> parameters, List<ModelTensor> modelTensors) throws IOException {

        String postProcessFunction = connector.getPostProcessFunction();
        Optional<String> processedResponse = executePostprocessFunction(scriptService, postProcessFunction, parameters, modelResponse);

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

    public static SdkHttpFullRequest signRequest(SdkHttpFullRequest request, String accessKey, String secretKey, String signingName, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        Aws4SignerParams params = Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingName(signingName)
                .signingRegion(Region.of(region))
                .build();

        return signer.sign(request, params);
    }
}
