/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.connector.Connector;
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
import static org.opensearch.ml.common.connector.MLPostProcessFunction.POST_PROCESS_FUNCTION;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.PRE_PROCESS_FUNCTION;
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
            List<String> docs = new ArrayList<>(inputDataSet.getDocs());
            Map<String, Object> params = ImmutableMap.of("text_docs", docs);
            String preProcessFunction = null;
            if (connector.getParameters() != null && connector.getParameters().containsKey(PRE_PROCESS_FUNCTION)) {
                preProcessFunction = connector.getParameters().get(PRE_PROCESS_FUNCTION);
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
        inputData.getParameters().entrySet().forEach(entry -> {
            entry.setValue(escapeJava(entry.getValue()));
        });
        return inputData;
    }

    public static ModelTensors processOutput(String modelResponse, Connector connector, ScriptService scriptService, Map<String, String> parameters) throws IOException {
        List<ModelTensor> modelTensors = new ArrayList<>();
        String postProcessFunction = null;
        if (parameters != null && parameters.containsKey(POST_PROCESS_FUNCTION)) {
            postProcessFunction = parameters.get(POST_PROCESS_FUNCTION);
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
