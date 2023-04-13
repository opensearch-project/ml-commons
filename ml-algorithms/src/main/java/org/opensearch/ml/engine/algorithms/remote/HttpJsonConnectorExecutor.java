/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.utils.ScriptUtils;
import org.opensearch.script.ScriptService;

import java.io.IOException;
import java.rmi.Remote;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.connector.ConnectorNames.HTTP_V1;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePostprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.executePreprocessFunction;
import static org.opensearch.ml.engine.utils.ScriptUtils.gson;

@Log4j2
@ConnectorExecutor(HTTP_V1)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor{

    private HttpConnector connector;
    @Setter
    private ScriptService scriptService;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (HttpConnector)connector;
    }

    @Override
    public ModelTensorOutput execute(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();

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

        AtomicReference<String> responseRef = new AtomicReference<>("");

        String payload = connector.createPayload(parameters);

        HttpUriRequest request;
        switch (connector.getHttpMethod().toUpperCase(Locale.ROOT)) {
            case "POST":
                try {
                    request = new HttpPost(connector.getEndpoint());
                    HttpEntity entity = new StringEntity(payload);
                    ((HttpPost)request).setEntity(entity);
                } catch (Exception e) {
                    throw new MLException("Failed to create http request for remote model", e);
                }
                break;
            case "GET":
                try {
                    request = new HttpGet(connector.getEndpoint());
                } catch (Exception e) {
                    throw new MLException("Failed to create http request for remote model", e);
                }
                break;
            default:
                throw new IllegalArgumentException("unsupported http method");
        }

        try {
            Map<String, ?> headers = connector.createHeaders();
            for (String key : headers.keySet()) {
                request.addHeader(key, (String)headers.get(key));
            }
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                     CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity responseEntity = response.getEntity();
                    String responseBody = EntityUtils.toString(responseEntity);
                    EntityUtils.consume(responseEntity);
                    responseRef.set(responseBody);
                }
                return null;
            });
            String modelResponse = responseRef.get();

            String postProcessFunction = connector.getPostProcessFunction();
            Optional<String> processedResponse = executePostprocessFunction(scriptService, postProcessFunction, parameters, modelResponse);

            connector.parseResponse(processedResponse.orElse(modelResponse), modelTensors, postProcessFunction != null);
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(modelTensors).build();
            tensorOutputs.add(tensors);
            return new ModelTensorOutput(tensorOutputs);
        } catch (PrivilegedActionException e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        } catch (IOException e) {
            log.error("Fail to parse http response", e);
            throw new RuntimeException(e);
        }
    }
}
