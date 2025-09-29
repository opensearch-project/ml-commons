
/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

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

    private BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

    @Setter
    @Getter
    private StreamTransportService streamTransportService;

    public AwsConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (AwsConnector) connector;
        Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection);
        this.bedrockRuntimeAsyncClient = null;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @SuppressWarnings("removal")
    @Override
    public void invokeRemoteService(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        try {
            SdkHttpFullRequest request;
            switch (connector.getActionHttpMethod(action).toUpperCase(Locale.ROOT)) {
                case "POST":
                    log.debug("original payload to remote model: " + payload);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, POST);
                    break;
                case "GET":
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, null, GET);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(signRequest(request))
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(
                    new MLSdkAsyncHttpResponseHandler(
                        executionContext,
                        actionListener,
                        parameters,
                        connector,
                        scriptService,
                        mlGuard,
                        action
                    )
                )
                .build();
            AccessController.doPrivileged((PrivilegedExceptionAction<CompletableFuture<Void>>) () -> httpClient.execute(executeRequest));
        } catch (RuntimeException exception) {
            log.error("Failed to execute {} in aws connector: {}", action, exception.getMessage(), exception);
            actionListener.onFailure(exception);
        } catch (Throwable e) {
            log.error("Failed to execute {} in aws connector", action, e);
            actionListener.onFailure(new MLException("Fail to execute " + action + " in aws connector", e));
        }
    }

    @Override
    public void invokeRemoteServiceStream(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        StreamPredictActionListener<MLTaskResponse, ?> actionListener
    ) {
        try {
            AtomicBoolean isStreamClosed = new AtomicBoolean(false);
            String llmInterface = parameters.get(LLM_INTERFACE);
            llmInterface = llmInterface.trim().toLowerCase(Locale.ROOT);
            llmInterface = StringEscapeUtils.unescapeJava(llmInterface);
            validateLLMInterface(llmInterface);

            ConverseStreamRequest request = ConverseStreamRequest
                .builder()
                .modelId(parameters.get("model"))
                .messages(Message.builder().role("user").content(ContentBlock.builder().text(parameters.get("inputs")).build()).build())
                .build();

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder().onResponse(response -> {
                // Handle initial response
                log.debug("Initial converse stream response: {}", response);
            }).onError(error -> {
                // Handle errors
                log.error("Converse stream error: {}", error.getMessage());
                actionListener.onFailure(new MLException("Error from remote service: " + error.getMessage(), error));
            }).onComplete(() -> {
                // Handle completion
                log.debug("Converse stream complete");
                sendCompletionResponse(isStreamClosed, actionListener);
            }).subscriber(event -> {
                log.debug("Converse stream event: {}", event);
                switch (event.sdkEventType()) {
                    case CONTENT_BLOCK_DELTA:
                        ContentBlockDeltaEvent contentEvent = (ContentBlockDeltaEvent) event;
                        String chunk = contentEvent.delta().text();
                        sendContentResponse(chunk, false, actionListener);
                        break;
                    default:
                        // Ignore the other event types for now.
                        break;
                }
            }).build();
            if (bedrockRuntimeAsyncClient == null) {
                bedrockRuntimeAsyncClient = buildBedrockRuntimeAsyncClient(httpClient);
            }
            bedrockRuntimeAsyncClient.converseStream(request, handler);
        } catch (Exception e) {
            log.error("Failed to execute streaming", e);
            actionListener.onFailure(new MLException("Fail to execute streaming", e));
        }
    }

    private BedrockRuntimeAsyncClient buildBedrockRuntimeAsyncClient(SdkAsyncHttpClient sdkAsyncHttpClient) {
        AwsCredentialsProvider awsCredentialsProvider;
        if (connector.getSessionToken() != null) {
            AwsSessionCredentials credentials = AwsSessionCredentials
                .create(connector.getAccessKey(), connector.getSecretKey(), connector.getSessionToken());
            awsCredentialsProvider = StaticCredentialsProvider.create(credentials);
        } else {
            awsCredentialsProvider = StaticCredentialsProvider
                .create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(connector.getAccessKey(), connector.getSecretKey())
                );
        }

        return BedrockRuntimeAsyncClient
            .builder()
            .region(Region.of(connector.getRegion()))
            .credentialsProvider(awsCredentialsProvider)
            .httpClient(sdkAsyncHttpClient)
            .build();
    }

    private SdkHttpFullRequest signRequest(SdkHttpFullRequest request) {
        String accessKey = connector.getAccessKey();
        String secretKey = connector.getSecretKey();
        String sessionToken = connector.getSessionToken();
        String signingName = connector.getServiceName();
        String region = connector.getRegion();

        return ConnectorUtils.signRequest(request, accessKey, secretKey, sessionToken, signingName, region);
    }

    private void validateLLMInterface(String llmInterface) {
        switch (llmInterface) {
            case LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE:
                break;
            default:
                throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
        }
    }
}
