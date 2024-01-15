/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.CONVERT_INPUT_TO_JSON_STRING;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.PROCESS_REMOTE_INFERENCE_INPUT;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.common.utils.StringUtils.processTextDocs;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostProcessFunction;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.DefaultPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.RemoteInferencePreProcessFunction;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

@Log4j2
public class ConnectorUtils {

    private static final Aws4Signer signer;
    static {
        signer = Aws4Signer.create();
    }

    public static RemoteInferenceInputDataSet processInput(
        MLInput mlInput,
        Connector connector,
        Map<String, String> parameters,
        ScriptService scriptService
    ) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Input is null");
        }
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        if (predictAction.isEmpty()) {
            throw new IllegalArgumentException("no predict action found");
        }
        RemoteInferenceInputDataSet inputData = processMLInput(mlInput, connector, parameters, scriptService);
        escapeRemoteInferenceInputData(inputData);
        return inputData;
    }

    private static RemoteInferenceInputDataSet processMLInput(
        MLInput mlInput,
        Connector connector,
        Map<String, String> parameters,
        ScriptService scriptService
    ) {
        String preProcessFunction = getPreprocessFunction(mlInput, connector);
        if (preProcessFunction == null) {
            if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
                return (RemoteInferenceInputDataSet) mlInput.getInputDataset();
            } else {
                throw new IllegalArgumentException("pre_process_function not defined in connector");
            }
        } else {
            preProcessFunction = fillProcessFunctionParameter(parameters, preProcessFunction);
            if (MLPreProcessFunction.contains(preProcessFunction)) {
                Function<MLInput, RemoteInferenceInputDataSet> function = MLPreProcessFunction.get(preProcessFunction);
                return function.apply(mlInput);
            } else if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
                if (parameters.containsKey(PROCESS_REMOTE_INFERENCE_INPUT)
                    && Boolean.parseBoolean(parameters.get(PROCESS_REMOTE_INFERENCE_INPUT))) {
                    Map<String, String> params = new HashMap<>();
                    params.putAll(connector.getParameters());
                    params.putAll(parameters);
                    RemoteInferencePreProcessFunction function = new RemoteInferencePreProcessFunction(
                        scriptService,
                        preProcessFunction,
                        params
                    );
                    return function.apply(mlInput);
                } else {
                    return (RemoteInferenceInputDataSet) mlInput.getInputDataset();
                }
            } else {
                MLInput newInput = escapeMLInput(mlInput);
                boolean convertInputToJsonString = parameters.containsKey(CONVERT_INPUT_TO_JSON_STRING)
                    && Boolean.parseBoolean(parameters.get(CONVERT_INPUT_TO_JSON_STRING));
                DefaultPreProcessFunction function = DefaultPreProcessFunction
                    .builder()
                    .scriptService(scriptService)
                    .preProcessFunction(preProcessFunction)
                    .convertInputToJsonString(convertInputToJsonString)
                    .build();
                return function.apply(newInput);
            }
        }
    }

    private static MLInput escapeMLInput(MLInput mlInput) {
        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
            List<String> newDocs = processTextDocs(docs);
            TextDocsInputDataSet newInputData = ((TextDocsInputDataSet) mlInput.getInputDataset()).toBuilder().docs(newDocs).build();
            return mlInput.toBuilder().inputDataset(newInputData).build();
        }

        if (mlInput.getInputDataset() instanceof TextSimilarityInputDataSet) {
            String query = ((TextSimilarityInputDataSet) mlInput.getInputDataset()).getQueryText();
            String newQuery = processTextDoc(query);
            List<String> docs = ((TextSimilarityInputDataSet) mlInput.getInputDataset()).getTextDocs();
            List<String> newDocs = processTextDocs(docs);
            TextSimilarityInputDataSet newInputData = ((TextSimilarityInputDataSet) mlInput.getInputDataset())
                .toBuilder()
                .queryText(newQuery)
                .textDocs(newDocs)
                .build();
            return mlInput.toBuilder().inputDataset(newInputData).build();
        }
        return mlInput;
    }

    public static void escapeRemoteInferenceInputData(RemoteInferenceInputDataSet inputData) {
        Map<String, String> newParameters = new HashMap<>();
        if (inputData.getParameters() != null) {
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
    }

    private static String getPreprocessFunction(MLInput mlInput, Connector connector) {
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        String preProcessFunction = predictAction.get().getPreProcessFunction();
        if (preProcessFunction != null) {
            return preProcessFunction;
        }
        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            return MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT;
        }
        return null;
    }

    public static ModelTensors processOutput(
        String modelResponse,
        Connector connector,
        ScriptService scriptService,
        Map<String, String> parameters
    ) throws IOException {
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
        postProcessFunction = fillProcessFunctionParameter(parameters, postProcessFunction);

        String responseFilter = parameters.get(RESPONSE_FILTER_FIELD);
        if (MLPostProcessFunction.contains(postProcessFunction)) {
            // in this case, we can use jsonpath to build a List<List<Float>> result from model response.
            if (StringUtils.isBlank(responseFilter))
                responseFilter = MLPostProcessFunction.getResponseFilter(postProcessFunction);

            Object filteredOutput = JsonPath.read(modelResponse, responseFilter);
            List<ModelTensor> processedResponse = MLPostProcessFunction.get(postProcessFunction).apply(filteredOutput);
            return ModelTensors.builder().mlModelTensors(processedResponse).build();
        }

        // execute user defined painless script.
        Optional<String> processedResponse = executePostProcessFunction(scriptService, postProcessFunction, modelResponse);
        String response = processedResponse.orElse(modelResponse);
        boolean scriptReturnModelTensor = postProcessFunction != null
            && processedResponse.isPresent()
            && org.opensearch.ml.common.utils.StringUtils.isJson(response);
        if (responseFilter == null) {
            connector.parseResponse(response, modelTensors, scriptReturnModelTensor);
        } else {
            Object filteredResponse = JsonPath.parse(response).read(parameters.get(RESPONSE_FILTER_FIELD));
            connector.parseResponse(filteredResponse, modelTensors, scriptReturnModelTensor);
        }
        return ModelTensors.builder().mlModelTensors(modelTensors).build();
    }

    private static String fillProcessFunctionParameter(Map<String, String> parameters, String processFunction) {
        if (processFunction != null && processFunction.contains("${parameters.")) {
            Map<String, String> tmpParameters = new HashMap<>();
            for (String key : parameters.keySet()) {
                tmpParameters.put(key, gson.toJson(parameters.get(key)));
            }
            StringSubstitutor substitutor = new StringSubstitutor(tmpParameters, "${parameters.", "}");
            processFunction = substitutor.replace(processFunction);
        }
        return processFunction;
    }

    public static SdkHttpFullRequest signRequest(
        SdkHttpFullRequest request,
        String accessKey,
        String secretKey,
        String sessionToken,
        String signingName,
        String region
    ) {
        AwsCredentials credentials = sessionToken == null
            ? AwsBasicCredentials.create(accessKey, secretKey)
            : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);

        Aws4SignerParams params = Aws4SignerParams
            .builder()
            .awsCredentials(credentials)
            .signingName(signingName)
            .signingRegion(Region.of(region))
            .build();

        return signer.sign(request, params);
    }

    public static SdkHttpFullRequest buildSdkRequest(Connector connector, Map<String, String> parameters, String payload, SdkHttpMethod method, ActionListener<Queue<ModelTensors>> actionListener) {
        String endpoint = connector.getPredictEndpoint(parameters);
        String charset = parameters.containsKey("charset") ? parameters.get("charset") : "UTF-8";
        RequestBody requestBody = RequestBody.fromString(payload, Charset.forName(charset));
        if (requestBody.optionalContentLength().isEmpty()) {
            log.error("Content length is empty. Aborting request to remote model");
            actionListener.onFailure(new IllegalArgumentException("Content length is empty. Aborting request to remote model"));
        }
        SdkHttpFullRequest.Builder builder = SdkHttpFullRequest
            .builder()
            .method(method)
            .uri(URI.create(endpoint))
            .contentStreamProvider(requestBody.contentStreamProvider());
        Map<String, String> headers = connector.getDecryptedHeaders();
        if (headers != null) {
            for (String key : headers.keySet()) {
                builder.putHeader(key, headers.get(key));
            }
        }
        builder.putHeader("Content-Length", requestBody.optionalContentLength().get().toString());
        return builder.build();
    }
}
