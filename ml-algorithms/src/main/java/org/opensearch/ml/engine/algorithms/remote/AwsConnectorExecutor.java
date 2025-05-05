/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.opensearch.arrow.spi.StreamManager;
import org.opensearch.arrow.spi.StreamTicket;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.arrow.RemoteModelStreamProducer;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
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

    @Setter
    @Getter
    private StreamManager streamManager;
    private OkHttpClient okHttpClient;
    @Setter
    @Getter
    private ThreadPool threadPool;

    public AwsConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (AwsConnector) connector;
        Duration connectionTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getConnectionTimeout());
        Duration readTimeout = Duration.ofSeconds(super.getConnectorClientConfig().getReadTimeout());
        Integer maxConnection = super.getConnectorClientConfig().getMaxConnections();
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient(connectionTimeout, readTimeout, maxConnection);
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                this.okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .retryOnConnectionFailure(true)
                    .build();
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to build OkHttpClient.", e);
        }
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
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        try {
            RemoteModelStreamProducer streamProducer = new RemoteModelStreamProducer();
            StreamTicket streamTicket = streamManager.registerStream(streamProducer, null);
            getLogger().info("[jngz] stream ticket: {}", streamTicket);
            List<ModelTensor> modelTensors = new ArrayList<>();
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(Map.of("stream_ticket", streamTicket)).build());
            getLogger().info("[jngz stream] spawn a new thread to execute onResponse which is waiting for streaming.");
            threadPool.executor("opensearch_ml_predict_remote").execute(() -> {
                actionListener.onResponse(new Tuple<>(0, new ModelTensors(modelTensors)));
            });
            EventSourceListener listener = new AwsEventSourceListener(getLogger(), true, streamProducer);
            Request request = ConnectorUtils.buildOKHttpRequestPOST(action, connector, parameters, payload);
            getLogger().info("[jngz stream] Stream request: {}", request);
            getLogger().info("[jngz stream] Stream request body: {}", request.body());
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                final EventSource eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener);
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to build event source.", e);
        } catch (RuntimeException exception) {
            log.error("[stream]Failed to execute {} in aws connector: {}", action, exception.getMessage(), exception);
            actionListener.onFailure(exception);
        } catch (Throwable e) {
            log.error("[stream]Failed to execute {} in aws connector", action, e);
            actionListener.onFailure(new MLException("Fail to execute " + action + " in aws connector", e));
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

    public final class AwsEventSourceListener extends EventSourceListener {
        private final Logger logger;
        private final boolean debug;
        private int publishErrorCount;
        private RemoteModelStreamProducer streamProducer;

        public AwsEventSourceListener(final Logger logger, final boolean debug, RemoteModelStreamProducer streamProducer) {
            this.logger = logger;
            this.debug = debug;
            this.publishErrorCount = 0;
            this.streamProducer = streamProducer;
        }

        /***
         * Callback when the SSE endpoint connection is made.
         * @param eventSource the event source
         * @param response the response
         */
        @Override
        public void onOpen(EventSource eventSource, Response response) {
            logger.info("[jngz sse] Connected to SSE Endpoint.");
        }

        /***
         * For each event received from the SSE endpoint
         * @param eventSource The event source
         * @param id The id of the event
         * @param type The type of the event which is used to filter
         * @param data The event data
         */
        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            log.info("[jngz sse] The event id is {} and the type is {}.", id, type);
            switch (type) {
                case "content_block_start":
                    log.info("Got the content start.");
                    break;
                case "content_block_delta":
                    log.info("Content Delta: {}", data);
                    streamProducer.getQueue().offer(data);
                    break;
                case "content_block_stop":
                    log.info("Got the content stop.");
                    streamProducer.getIsStop().set(true);
                    break;
                default:
                    log.info("[No action] Got the content type: {}.", type);
                    break;
            }
        }

        /***
         * When the connection is closed we receive this even which is currently only logged.
         * @param eventSource The event source
         */
        @Override
        public void onClosed(EventSource eventSource) {
            logger.info("[jngz sse] Closed");
        }

        /***
         * If there is any failure we log the error and the stack trace
         * During stream resets with no errors we set the connected flag to false to allow the main thread to attempt a re-connect
         * @param eventSource The event source
         * @param t The error object
         * @param response The response
         */
        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            logger.error("[jngz sse] onFailure");
            if (t != null) {
                logger.error("Error: " + t.getMessage(), t);
                if (t instanceof StreamResetException && t.getMessage().contains("NO_ERROR")) {
                    // TODO: reconnect
                } else {
                    streamProducer.setProduceError(true);
                    throw new MLException("SSE failure 2.", t);
                }
            }
        }
    }
}
