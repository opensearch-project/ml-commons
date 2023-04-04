/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

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
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.connector.ConnectorNames.HTTP_V1;

@Log4j2
@ConnectorExecutor(HTTP_V1)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor{

    private HttpConnector connector;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (HttpConnector)connector;
    }

    @Override
    public ModelTensorOutput execute(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();

        RemoteInferenceInputDataSet inputData = (RemoteInferenceInputDataSet)mlInput.getInputDataset();;
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
        } catch (PrivilegedActionException e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        }

        connector.parseResponse(responseRef.get(), modelTensors);
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(modelTensors).build();
        tensorOutputs.add(tensors);
        return new ModelTensorOutput(tensorOutputs);
    }
}
