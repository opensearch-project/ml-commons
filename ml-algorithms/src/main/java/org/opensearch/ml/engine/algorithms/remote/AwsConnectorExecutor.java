/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.opensearch.client.Client;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
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
@ConnectorExecutor(AWS_SIGV4)
public class AwsConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private AwsConnector connector;
    private final SdkAsyncHttpClient httpClient;
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

    public AwsConnectorExecutor(Connector connector) {
        this.connector = (AwsConnector) connector;
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient();
    }


    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, Queue<ModelTensors> tensorOutputs, CountDownLatch countDownLatch, ActionListener<Queue<ModelTensors>> actionListener) {
        try {
            SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(connector, parameters, payload, POST, actionListener);
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(signRequest(request))
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(new MLSdkAsyncHttpResponseHandler(countDownLatch, actionListener, parameters, tensorOutputs, connector, scriptService))
                .build();
            AccessController.doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> httpClient.execute(executeRequest));
        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in aws connector: " + exception.getMessage(), exception);
            throw exception;
        } catch (Throwable e) {
            log.error("Failed to execute predict in aws connector", e);
            throw new MLException("Fail to execute predict in aws connector", e);
        }
    }


    private SdkHttpFullRequest signRequest(SdkHttpFullRequest request) {
        String accessKey = connector.getAccessKey();
        String secretKey = connector.getSecretKey();
        String sessionToken = connector.getSessionToken();
        String signingName = connector.getServiceName();
        String region = connector.getRegion();

        return ConnectorUtils.signRequest(request, accessKey, secretKey, sessionToken, signingName, region);
    }
}
