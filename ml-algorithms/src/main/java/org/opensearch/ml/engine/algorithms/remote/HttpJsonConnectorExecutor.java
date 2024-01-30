/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.Client;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private HttpConnector connector;
    @Setter
    @Getter
    private ScriptService scriptService;

    @Setter
    @Getter
    private TokenBucket rateLimiter;
    @Setter
    @Getter
    private Map<String, TokenBucket> userRateLimiterMap;
    @Setter
    @Getter
    private Client client;

    private SdkAsyncHttpClient httpClient;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (HttpConnector) connector;
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient();
    }

    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, Map<Integer, ModelTensors> tensorOutputs, WrappedCountDownLatch countDownLatch, ActionListener<List<ModelTensors>> actionListener) {
        try {
            SdkHttpFullRequest request;
            switch (connector.getPredictHttpMethod().toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    request = ConnectorUtils.buildSdkRequest(connector, parameters, payload, POST, actionListener);
                    MLHttpClientFactory.validateIp(request.getUri().getHost());
                    break;
                case "GET":
                    request = ConnectorUtils.buildSdkRequest(connector, parameters, null, GET, actionListener);
                    MLHttpClientFactory.validateIp(request.getUri().getHost());
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(request)
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(new MLSdkAsyncHttpResponseHandler(countDownLatch, actionListener, parameters, tensorOutputs, connector, scriptService))
                .build();
            AccessController.doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> httpClient.execute(executeRequest));
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(new MLException("Fail to execute http connector", e));
        }
    }
}
