/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.function_calling.OpenaiV1ChatCompletionsFunctionCalling.FINISH_REASON_PATH;
import static software.amazon.awssdk.http.SdkHttpMethod.GET;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.net.URL;
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.opensearch.arrow.spi.StreamManager;
import org.opensearch.arrow.spi.StreamTicket;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.arrow.RemoteModelStreamProducer;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

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
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor extends AbstractConnectorExecutor {

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
    @Setter
    @Getter
    private MLGuard mlGuard;
    @Setter
    private volatile AtomicBoolean connectorPrivateIpEnabled;

    private SdkAsyncHttpClient httpClient;

    @Setter
    @Getter
    private StreamManager streamManager;
    private OkHttpClient okHttpClient;
    @Setter
    @Getter
    private ThreadPool threadPool;

    public HttpJsonConnectorExecutor(Connector connector) {
        super.initialize(connector);
        this.connector = (HttpConnector) connector;
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
                    validateHttpClientParameters(action, parameters);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, payload, POST);
                    break;
                case "GET":
                    validateHttpClientParameters(action, parameters);
                    request = ConnectorUtils.buildSdkRequest(action, connector, parameters, null, GET);
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(request)
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
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            actionListener.onFailure(new MLException("Fail to execute http connector", e));
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
            getLogger().debug("Stream ticket: {}", streamTicket);
            List<ModelTensor> modelTensors = new ArrayList<>();
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(Map.of("stream_ticket", streamTicket)).build());
            threadPool.executor("opensearch_ml_predict_stream").execute(() -> {
                actionListener.onResponse(new Tuple<>(0, new ModelTensors(modelTensors)));
            });
            String llmInterface = parameters.get(LLM_INTERFACE);
            llmInterface = llmInterface.trim().toLowerCase(Locale.ROOT);
            llmInterface = StringEscapeUtils.unescapeJava(llmInterface);
            validateLLMInterface(llmInterface);
            EventSourceListener listener = new HttpJsonConnectorExecutor.HTTPEventSourceListener(getLogger(), streamProducer, llmInterface);
            Request request = ConnectorUtils.buildOKHttpRequestPOST(action, connector, parameters, payload);
            getLogger().debug("Stream request: {}", request);
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                final EventSource eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, listener);
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Failed to build event source.", e);
        } catch (RuntimeException exception) {
            log.error("Failed to execute {} in aws connector: {}", action, exception.getMessage(), exception);
            actionListener.onFailure(exception);
        } catch (Throwable e) {
            log.error("Failed to execute {} in aws connector", action, e);
            actionListener.onFailure(new MLException("Fail to execute " + action + " in aws connector", e));
        }
    }

    private void validateHttpClientParameters(String action, Map<String, String> parameters) throws Exception {
        String endpoint = connector.getActionEndpoint(action, parameters);
        URL url = new URL(endpoint);
        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        MLHttpClientFactory.validate(protocol, host, port, connectorPrivateIpEnabled);
    }

    private void validateLLMInterface(String llmInterface) {
        switch (llmInterface) {
            case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                break;
            default:
                throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
        }
    }

    public final class HTTPEventSourceListener extends EventSourceListener {
        private final Logger logger;
        private RemoteModelStreamProducer streamProducer;
        private final String llmInterface;

        public HTTPEventSourceListener(final Logger logger, RemoteModelStreamProducer streamProducer, String llmInterface) {
            this.logger = logger;
            this.streamProducer = streamProducer;
            this.llmInterface = llmInterface;
        }

        /***
         * Callback when the SSE endpoint connection is made.
         * @param eventSource the event source
         * @param response the response
         */
        @Override
        public void onOpen(EventSource eventSource, Response response) {
            logger.debug("Connected to SSE Endpoint.");
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
            log.debug("The event id is {} and the type is {}.", id, type);
            log.debug("The data is: {}", data);
            switch (llmInterface) {
                case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                    onOpenAIEvent(data);
                    break;
                default:
                    throw new MLException(String.format("Unsupported llm interface: %s", llmInterface));
            }
        }

        /***
         * When the connection is closed we receive this even which is currently only logged.
         * @param eventSource The event source
         */
        @Override
        public void onClosed(EventSource eventSource) {
            logger.debug("SSE CLOSED.");
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
            logger.error("SSE failure.");
            if (t != null) {
                logger.error("Error: " + t.getMessage(), t);
                if (t instanceof StreamResetException && t.getMessage().contains("NO_ERROR")) {
                    // TODO: reconnect
                } else {
                    streamProducer.setProduceError(true);
                    throw new MLException("SSE failure.", t);
                }
            }
        }

        private void onOpenAIEvent(String data) {
            if (data.contentEquals("[DONE]")) {
                streamProducer.getIsStop().set(true);
                return;
            }
            Map<String, Object> dataMap = StringUtils.fromJson(data, "data");
            String llmFinishReason = JsonPath.read(dataMap, FINISH_REASON_PATH);
            if (llmFinishReason != null && llmFinishReason.contentEquals("stop")) {
                return;
            }
            String deltaContent = JsonPath.read(dataMap, "$.choices[0].delta.content");
            streamProducer.getQueue().offer(deltaContent);
        }

        // For future anthropic support.
        private void onClaudeEvent(String data) {
            Map<String, Object> dataMap = StringUtils.fromJson(data, "data");
            if (dataMap.containsKey("type") && ((String) dataMap.get("type")).contentEquals("message_stop")) {
                streamProducer.getIsStop().set(true);
                return;
            }
            streamProducer.getQueue().offer(data);
        }
    }
}
