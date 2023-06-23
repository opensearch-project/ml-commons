/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.connector.ConnectorNames.HTTP_V1;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processInput;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

@Log4j2
@ConnectorExecutor(HTTP_V1)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor {

    private AbstractConnector connector;
    @Setter
    private ScriptService scriptService;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (AbstractConnector)connector;
    }

    @Override
    public ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();

        try {
            Map<String, String> parameters = new HashMap<>();
            if (connector.getParameters() != null) {
                parameters.putAll(connector.getParameters());
            }
            RemoteInferenceInputDataSet inputData = processInput(mlInput, connector, scriptService);
            if (inputData.getParameters() != null) {
                parameters.putAll(inputData.getParameters());
            }
            String payload = connector.createPredictPayload(parameters);
            AtomicReference<String> responseRef = new AtomicReference<>("");

            HttpUriRequest request;
            switch (connector.getPredictHttpMethod().toUpperCase(Locale.ROOT)) {
                case "POST":
                    try {
                        request = new HttpPost(connector.getPredictEndpoint());
                        HttpEntity entity = new StringEntity(payload);
                        ((HttpPost)request).setEntity(entity);
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                case "GET":
                    try {
                        request = new HttpGet(connector.getPredictEndpoint());
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            Map<String, ?> headers = connector.getDecryptedHeaders();

            boolean hasContentTypeHeader = false;
            for (String key : headers.keySet()) {
                request.addHeader(key, (String)headers.get(key));
                if (key.toLowerCase().equals("Content-Type")) {
                    hasContentTypeHeader = true;
                }
            }
            if (!hasContentTypeHeader) {
                request.addHeader("Content-Type", "application/json");
            }

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                try (CloseableHttpClient httpClient = MLHttpClientFactory.getCloseableHttpClient();
                     CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity responseEntity = response.getEntity();
                    String responseBody = EntityUtils.toString(responseEntity);
                    EntityUtils.consume(responseEntity);
                    responseRef.set(responseBody);
                }
                return null;
            });
            String modelResponse = responseRef.get();

            ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters, modelTensors);
            tensorOutputs.add(tensors);
            return new ModelTensorOutput(tensorOutputs);
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        }
    }
}
