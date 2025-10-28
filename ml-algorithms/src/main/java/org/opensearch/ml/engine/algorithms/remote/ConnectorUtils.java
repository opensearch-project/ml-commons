/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_PREDICT;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.CANCEL_BATCH_PREDICT;
import static org.opensearch.ml.common.connector.ConnectorAction.BEDROCK;
import static org.opensearch.ml.common.connector.ConnectorAction.COHERE;
import static org.opensearch.ml.common.connector.ConnectorAction.OPENAI;
import static org.opensearch.ml.common.connector.ConnectorAction.SAGEMAKER;
import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.common.connector.HttpConnector.RESPONSE_FILTER_FIELD;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.CONVERT_INPUT_TO_JSON_STRING;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.PROCESS_REMOTE_INFERENCE_INPUT;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.processTextDoc;
import static org.opensearch.ml.common.utils.StringUtils.processTextDocs;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostProcessFunction;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
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
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.processor.ProcessorChain;
import org.opensearch.script.ScriptService;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.Request;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

@Log4j2
public class ConnectorUtils {

    private static final AwsV4HttpSigner signer;
    public static final String SKIP_VALIDATE_MISSING_PARAMETERS = "skip_validating_missing_parameters";

    static {
        signer = AwsV4HttpSigner.create();
    }

    /**
     * Determines the protocol based on parameters and credentials
     */
    public static String determineProtocol(Map<String, String> parameters, Map<String, String> credential) {
        boolean hasAwsRegion = parameters != null && parameters.containsKey("region");
        boolean hasAwsServiceName = parameters != null && parameters.containsKey("service_name");
        boolean hasRoleArn = credential != null && credential.containsKey("roleArn");
        boolean hasAwsCredential = credential != null && credential.containsKey("access_key") && credential.containsKey("secret_key");
        // Check if service_name is in parameters (indicates AWS SigV4)
        if (hasAwsRegion && hasAwsServiceName && (hasRoleArn || hasAwsCredential)) {
            return AWS_SIGV4;
        }
        // Default to http (for basic auth or other)
        return HTTP;
    }

    public static RemoteInferenceInputDataSet processInput(
        String action,
        MLInput mlInput,
        Connector connector,
        Map<String, String> parameters,
        ScriptService scriptService
    ) {
        if (mlInput == null) {
            throw new IllegalArgumentException("Input is null");
        }
        Optional<ConnectorAction> connectorAction = connector.findAction(action);
        if (connectorAction.isEmpty()) {
            throw new IllegalArgumentException("no " + action + " action found");
        }
        RemoteInferenceInputDataSet inputData = processMLInput(action, mlInput, connector, parameters, scriptService);
        escapeRemoteInferenceInputData(inputData);
        return inputData;
    }

    private static RemoteInferenceInputDataSet processMLInput(
        String action,
        MLInput mlInput,
        Connector connector,
        Map<String, String> parameters,
        ScriptService scriptService
    ) {
        String preProcessFunction = getPreprocessFunction(action, mlInput, connector);
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
        inputData.setParameters(escapeRemoteInferenceInputData(inputData.getParameters()));
    }

    public static Map<String, String> escapeRemoteInferenceInputData(Map<String, String> parameters) {
        if (parameters == null) {
            return parameters;
        }
        Map<String, String> newParameters = new HashMap<>();
        String noEscapeParams = parameters.get(NO_ESCAPE_PARAMS);
        Set<String> noEscapParamSet = new HashSet<>();
        if (noEscapeParams != null && !noEscapeParams.isEmpty()) {
            String[] keys = noEscapeParams.split(",");
            for (String key : keys) {
                noEscapParamSet.add(key.trim());
            }
        }
        parameters.forEach((key, value) -> {
            if (value == null) {
                newParameters.put(key, null);
            } else if (org.opensearch.ml.common.utils.StringUtils.isJson(value)) {
                // no need to escape if it's already valid json
                newParameters.put(key, value);
            } else if (!noEscapParamSet.contains(key)) {
                newParameters.put(key, escapeJson(value));
            } else {
                newParameters.put(key, value);
            }
        });
        return newParameters;
    }

    private static String getPreprocessFunction(String action, MLInput mlInput, Connector connector) {
        Optional<ConnectorAction> connectorAction = connector.findAction(action);
        String preProcessFunction = connectorAction.get().getPreProcessFunction();
        if (preProcessFunction != null) {
            return preProcessFunction;
        }
        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            return MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT;
        }
        return null;
    }

    public static ModelTensors processOutput(
        String action,
        String modelResponse,
        Connector connector,
        ScriptService scriptService,
        Map<String, String> parameters,
        MLGuard mlGuard
    ) throws IOException {
        if (modelResponse == null) {
            throw new IllegalArgumentException("model response is null");
        }
        if (mlGuard != null
            && !mlGuard
                .validate(
                    modelResponse,
                    MLGuard.Type.OUTPUT,
                    Map.of("question", org.opensearch.ml.common.utils.StringUtils.processTextDoc(modelResponse))
                )) {
            throw new IllegalArgumentException("guardrails triggered for LLM output");
        }
        List<ModelTensor> modelTensors = new ArrayList<>();
        Optional<ConnectorAction> connectorAction = connector.findAction(action);
        if (connectorAction.isEmpty()) {
            throw new IllegalArgumentException("no " + action + " action found");
        }
        String postProcessFunction = connectorAction.get().getPostProcessFunction();
        postProcessFunction = fillProcessFunctionParameter(parameters, postProcessFunction);

        String responseFilter = parameters.get(RESPONSE_FILTER_FIELD);
        if (MLPostProcessFunction.contains(postProcessFunction)) {
            // in this case, we can use jsonpath to build a List<List<Float>> result from model response.
            if (StringUtils.isBlank(responseFilter)) {
                responseFilter = MLPostProcessFunction.getResponseFilter(postProcessFunction);
            }
            Object filteredOutput = JsonPath.read(modelResponse, responseFilter);
            MLResultDataType dataType = parseMLResultDataTypeFromResponseFilter(responseFilter);
            List<ModelTensor> processedResponse = MLPostProcessFunction.get(postProcessFunction).apply(filteredOutput, dataType);
            return ModelTensors.builder().mlModelTensors(processedResponse).build();
        }

        // execute user defined painless script.
        Optional<String> processedResponse = executePostProcessFunction(scriptService, postProcessFunction, modelResponse);
        String response = processedResponse.orElse(modelResponse);
        boolean scriptReturnModelTensor = postProcessFunction != null
            && processedResponse.isPresent()
            && org.opensearch.ml.common.utils.StringUtils.isJson(response);

        // Apply output processor chain if configured
        Object processedOutput;
        // Apply output processor chain if configured
        List<Map<String, Object>> processorConfigs = ProcessorChain.extractProcessorConfigs(parameters);
        if (!processorConfigs.isEmpty()) {
            ProcessorChain processorChain = new ProcessorChain(processorConfigs);

            if (responseFilter != null) {
                // Apply filter first, then processor chain
                Object filteredResponse = JsonPath.parse(response).read(responseFilter);
                processedOutput = processorChain.process(filteredResponse);
            } else {
                // Apply processor chain to whole response
                processedOutput = processorChain.process(response);
            }

            // Handle the processed output
            connector.parseResponse(processedOutput, modelTensors, scriptReturnModelTensor);
        } else {
            // Original flow without processor chain
            if (responseFilter == null) {
                connector.parseResponse(response, modelTensors, scriptReturnModelTensor);
            } else {
                Object filteredResponse = JsonPath.parse(response).read(responseFilter);
                connector.parseResponse(filteredResponse, modelTensors, scriptReturnModelTensor);
            }
        }
        return ModelTensors.builder().mlModelTensors(modelTensors).build();
    }

    private static MLResultDataType parseMLResultDataTypeFromResponseFilter(String responseFilter) {
        for (MLResultDataType type : MLResultDataType.values()) {
            if (StringUtils.containsIgnoreCase(responseFilter, "." + type.name())) {
                return type;
            }
        }
        return null;
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

        SignedRequest signedRequest = signer
            .sign(
                r -> r
                    .identity(credentials)
                    .request(request)
                    .payload(request.contentStreamProvider().orElse(null))
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, signingName)
                    .putProperty(AwsV4HttpSigner.REGION_NAME, region)
            );
        return (SdkHttpFullRequest) signedRequest.request();
    }

    public static SdkHttpFullRequest buildSdkRequest(
        String action,
        Connector connector,
        Map<String, String> parameters,
        String payload,
        SdkHttpMethod method
    ) {
        String charset = parameters.getOrDefault("charset", "UTF-8");
        RequestBody requestBody;
        if (payload != null) {
            requestBody = RequestBody.fromString(payload, Charset.forName(charset));
        } else {
            requestBody = RequestBody.empty();
        }
        if (SdkHttpMethod.POST == method
            && 0 == requestBody.optionalContentLength().get()
            && !action.equals(CANCEL_BATCH_PREDICT.toString())) {
            log.error("Content length is 0. Aborting request to remote model");
            throw new IllegalArgumentException("Content length is 0. Aborting request to remote model");
        }
        String endpoint = connector.getActionEndpoint(action, parameters);
        URI uri;
        try {
            uri = URI.create(endpoint);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid URI" + ". Please check if the endpoint is valid from connector.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Encountered error when trying to create uri from endpoint in ml connector. Please update the endpoint in connection configuration: ",
                e
            );
        }

        SdkHttpFullRequest.Builder builder = SdkHttpFullRequest
            .builder()
            .method(method)
            .uri(uri)
            .contentStreamProvider(requestBody.contentStreamProvider());
        Map<String, String> headers = connector.getDecryptedHeaders();
        if (headers != null) {
            for (String key : headers.keySet()) {
                builder.putHeader(key, headers.get(key));
            }
        }
        if (builder.matchingHeaders("Content-Type").isEmpty()) {
            builder.putHeader("Content-Type", "application/json");
        }
        if (builder.matchingHeaders("Content-Length").isEmpty()) {
            builder.putHeader("Content-Length", requestBody.optionalContentLength().get().toString());
        }
        return builder.build();
    }

    public static Request buildOKHttpStreamingRequest(String action, Connector connector, Map<String, String> parameters, String payload) {
        okhttp3.RequestBody requestBody;
        if (payload != null) {
            requestBody = okhttp3.RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));
        } else {
            throw new IllegalArgumentException("Content length is 0. Aborting request to remote model");
        }

        String endpoint = connector.getActionEndpoint(action, parameters);
        URI uri;
        try {
            uri = URI.create(endpoint);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid URI" + ". Please check if the endpoint is valid from connector.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Encountered error when trying to create uri from endpoint in ml connector. Please update the endpoint in connection configuration: ",
                e
            );
        }
        Request.Builder requestBuilder = new Request.Builder();
        Map<String, String> headers = connector.getDecryptedHeaders();
        if (headers != null) {
            for (String key : headers.keySet()) {
                requestBuilder.addHeader(key, headers.get(key));
            }
        }

        // Add SSE-specific headers
        requestBuilder.addHeader("Accept-Encoding", "");
        requestBuilder.addHeader("Accept", "text/event-stream");
        requestBuilder.addHeader("Cache-Control", "no-cache");
        requestBuilder.url(endpoint);
        requestBuilder.post(requestBody);
        Request request = requestBuilder.build();

        return request;
    }

    public static ConnectorAction createConnectorAction(Connector connector, ConnectorAction.ActionType actionType) {
        Optional<ConnectorAction> batchPredictAction = connector.findAction(BATCH_PREDICT.name());
        String predictEndpoint = batchPredictAction.get().getUrl();
        Map<String, String> parameters = connector.getParameters() != null
            ? new HashMap<>(connector.getParameters())
            : Collections.emptyMap();

        // Apply parameter substitution only if needed
        if (!parameters.isEmpty()) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            predictEndpoint = substitutor.replace(predictEndpoint);
        }

        boolean isCancelAction = actionType == CANCEL_BATCH_PREDICT;

        // Initialize the default method and requestBody
        String method = "POST";
        String requestBody = null;
        String url = "";

        switch (ConnectorAction.getRemoteServerFromURL(predictEndpoint)) {
            case SAGEMAKER:
                url = isCancelAction
                    ? predictEndpoint.replace("CreateTransformJob", "StopTransformJob")
                    : predictEndpoint.replace("CreateTransformJob", "DescribeTransformJob");
                requestBody = "{ \"TransformJobName\" : \"${parameters.TransformJobName}\"}";
                break;
            case OPENAI:
            case COHERE:
                url = isCancelAction ? predictEndpoint + "/${parameters.id}/cancel" : predictEndpoint + "/${parameters.id}";
                method = isCancelAction ? "POST" : "GET";
                break;
            case BEDROCK:
                url = isCancelAction
                    ? predictEndpoint + "/${parameters.processedJobArn}/stop"
                    : predictEndpoint + "/${parameters.processedJobArn}";
                method = isCancelAction ? "POST" : "GET";
                break;
            default:
                String errorMessage = isCancelAction
                    ? "Please configure the action type to cancel the batch job in the connector"
                    : "Please configure the action type to get the batch job details in the connector";
                throw new UnsupportedOperationException(errorMessage);
        }

        return ConnectorAction
            .builder()
            .actionType(actionType)
            .method(method)
            .url(url)
            .requestBody(requestBody)
            .headers(batchPredictAction.get().getHeaders())
            .build();
    }
}
