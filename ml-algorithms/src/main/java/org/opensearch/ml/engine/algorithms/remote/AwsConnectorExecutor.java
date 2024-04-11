/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.opensearch.client.Client;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
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
public class AwsConnectorExecutor extends AbstractConnectorExecutor {

    @Getter
    private AwsConnector connector;
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
    @Setter
    @Getter
    private MLGuard mlGuard;

    private SdkAsyncHttpClient httpClient;

    public AwsConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (AwsConnector) connector;
        Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection);
    }

    @SuppressWarnings("removal")
    @Override
    public void invokeRemoteModel(
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        Map<Integer, ModelTensors> tensorOutputs,
        WrappedCountDownLatch countDownLatch,
        ActionListener<List<ModelTensors>> actionListener
    ) {
        try {
            SdkHttpFullRequest request = ConnectorUtils.buildSdkRequest(connector, parameters, payload, POST);
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(signRequest(request))
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(
                    new MLSdkAsyncHttpResponseHandler(
                        countDownLatch,
                        actionListener,
                        parameters,
                        tensorOutputs,
                        connector,
                        scriptService,
                        mlGuard
                    )
                )
                .build();
            AccessController.doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> httpClient.execute(executeRequest));
        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in aws connector: " + exception.getMessage(), exception);
            actionListener.onFailure(exception);
        } catch (Throwable e) {
            log.error("Failed to execute predict in aws connector", e);
            actionListener.onFailure(new MLException("Fail to execute predict in aws connector", e));
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
