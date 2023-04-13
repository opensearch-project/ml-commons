/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.common.connector.ConnectorNames.AWS_V1;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePreprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.gson;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

@Log4j2
@ConnectorExecutor(AWS_V1)
public class AwsConnectorExecutor implements RemoteConnectorExecutor{

    private AwsConnector connector;
    private final Aws4Signer signer;
    private final SdkHttpClient httpClient;
    @Setter
    private ScriptService scriptService;

    public AwsConnectorExecutor(Connector connector) {
        this.connector = (AwsConnector)connector;
        this.signer = Aws4Signer.create();
        this.httpClient = new DefaultSdkHttpClientBuilder().build();
    }

    @Override
    public ModelTensorOutput execute(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();


        try {
            RemoteInferenceInputDataSet inputData = null;
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
                            processedParameters.put(key, gson.toJson(parametersMap.get(key)));
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

            Map<String, String> parameters = inputData.getParameters();

            String endpoint = connector.getEndpoint();
            String payload = connector.createPayload(parameters);
            RequestBody requestBody = RequestBody.fromString(payload);

            SdkHttpFullRequest.Builder builder = SdkHttpFullRequest.builder()
                    .method(POST)
                    .uri(URI.create(endpoint))
                    .contentStreamProvider(requestBody.contentStreamProvider());
            Map<String, String> headers = connector.createHeaders();
            for (String key : headers.keySet()) {
                builder.putHeader(key, headers.get(key));
            }
            SdkHttpFullRequest request = builder.build();
            HttpExecuteRequest executeRequest = HttpExecuteRequest.builder()
                    .request(signRequest(request))
                    .contentStreamProvider(request.contentStreamProvider().orElse(null))
                    .build();

            HttpExecuteResponse response = AccessController.doPrivileged((PrivilegedExceptionAction<HttpExecuteResponse>) () -> {
                return httpClient.prepareRequest(executeRequest).call();
            });

            AbortableInputStream body = null;
            if (response.responseBody().isPresent()) {
                body = response.responseBody().get();
            }

            StringBuilder responseBuilder = new StringBuilder();
            if (body != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
            }
            String modelResponse = responseBuilder.toString();

            String postProcessFunction = connector.getPostProcessFunction();
            Optional<String> processedResponse = executePostprocessFunction(scriptService, postProcessFunction, parameters, modelResponse);

            connector.parseResponse(processedResponse.orElse(modelResponse), modelTensors, postProcessFunction != null);
        } catch (Exception e) {
            log.error("Failed to execute aws connector", e);
            throw new RuntimeException(e);
        }
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(modelTensors).build();
        tensorOutputs.add(tensors);
        return new ModelTensorOutput(tensorOutputs);
    }

    private SdkHttpFullRequest signRequest(SdkHttpFullRequest request) {
        String accessKey = connector.getAccessKey();
        String secretKey = connector.getSecretKey();
        String signingName = connector.getServiceName();
        String region = connector.getRegion();
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        Aws4SignerParams params = Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingName(signingName)
                .signingRegion(Region.of(region))
                .build();

        return signer.sign(request, params);
    }
}
