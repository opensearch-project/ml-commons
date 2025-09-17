/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.CANCEL_BATCH_PREDICT;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections.MapUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.collect.Tuple;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

@Log4j2
public class MLSdkAsyncHttpResponseHandler implements SdkAsyncHttpResponseHandler {
    public static final String AMZ_ERROR_HEADER = "x-amzn-ErrorType";
    @Getter
    private Integer statusCode;
    @Getter
    private final StringBuilder responseBody = new StringBuilder();

    private final ExecutionContext executionContext;

    private final ActionListener<Tuple<Integer, ModelTensors>> actionListener;

    private final Map<String, String> parameters;

    private final Connector connector;

    private final String action;

    private final ScriptService scriptService;

    private final MLGuard mlGuard;

    // used to cache exceptions before the invocation of response()
    private AtomicReference<Exception> exceptionHolder = new AtomicReference<>();

    public MLSdkAsyncHttpResponseHandler(
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener,
        Map<String, String> parameters,
        Connector connector,
        ScriptService scriptService,
        MLGuard mlGuard,
        String action
    ) {
        this.executionContext = executionContext;
        this.actionListener = actionListener;
        this.parameters = parameters;
        this.connector = connector;
        this.scriptService = scriptService;
        this.mlGuard = mlGuard;
        this.action = action;
    }

    @Override
    public void onHeaders(SdkHttpResponse response) {
        SdkHttpFullResponse sdkResponse = (SdkHttpFullResponse) response;
        log.debug("received response headers: " + sdkResponse.headers());
        this.statusCode = sdkResponse.statusCode();
        if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_MULTIPLE_CHOICES) {
            log.error("Received error from remote service with status code {}, response headers: {}", statusCode, sdkResponse.headers());
            handleThrottlingInHeader(sdkResponse);
            // add more handling here for other exceptions in headers
        }
    }

    @Override
    public void onStream(Publisher<ByteBuffer> stream) {
        stream.subscribe(new MLResponseSubscriber());
    }

    @Override
    public void onError(Throwable error) {
        log.error("Received error from remote service: {}", error.getMessage(), error);
        RestStatus status = (statusCode == null) ? RestStatus.INTERNAL_SERVER_ERROR : RestStatus.fromCode(statusCode);
        String errorMessage = "Error communicating with remote model: " + error.getMessage();
        actionListener.onFailure(new OpenSearchStatusException(errorMessage, status));
    }

    private void handleException(Exception e) {
        if (exceptionHolder.get() == null) {
            exceptionHolder.compareAndSet(null, e);
        }
    }

    private void handleThrottlingInHeader(SdkHttpFullResponse sdkResponse) {
        if (MapUtils.isEmpty(sdkResponse.headers())) {
            return;
        }
        List<String> errorsInHeader = sdkResponse.headers().get(AMZ_ERROR_HEADER);
        if (errorsInHeader == null || errorsInHeader.isEmpty()) {
            return;
        }
        // Check the throttling exception from AMZN servers, e.g. sageMaker.
        // See [https://github.com/opensearch-project/ml-commons/issues/2429] for more details.
        boolean containsThrottlingException = errorsInHeader.stream().anyMatch(str -> str.startsWith("ThrottlingException"));
        if (containsThrottlingException) {
            log.error("Remote server returned error code: {}", statusCode);
            handleException(
                new RemoteConnectorThrottlingException(
                    REMOTE_SERVICE_ERROR
                        + "The request was denied due to remote server throttling. "
                        + "To change the retry policy and behavior, please update the connector client_config.",
                    RestStatus.fromCode(statusCode)
                )
            );
        }
    }

    protected class MLResponseSubscriber implements Subscriber<ByteBuffer> {
        private Subscription subscription;

        @Override
        public void onSubscribe(@NotNull Subscription s) {
            this.subscription = s;
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            responseBody.append(StandardCharsets.UTF_8.decode(byteBuffer));
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onError(Throwable t) {
            log
                .error(
                    "Error on receiving response body from remote: {}",
                    t instanceof NullPointerException ? "NullPointerException" : t.getMessage(),
                    t
                );
            response();
        }

        @Override
        public void onComplete() {
            response();
        }
    }

    private void response() {
        String body = responseBody.toString();
        log.debug("Received response from remote service: {}", body);
        if (exceptionHolder.get() != null) {
            log.error("Remote server returned exception with status code: {} and body: {}", statusCode, body);
            actionListener.onFailure(exceptionHolder.get());
            return;
        }

        // Handle error status codes (4xx, 5xx)
        if (statusCode == null || statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_MULTIPLE_CHOICES) {
            RestStatus status = (statusCode != null) ? RestStatus.fromCode(statusCode) : RestStatus.INTERNAL_SERVER_ERROR;
            String errorMsg = Strings.isBlank(body)
                ? String.format("Remote service returned error status %d with empty body", statusCode)
                : REMOTE_SERVICE_ERROR + body;

            log.error("Remote service returned error: {} with status: {}", errorMsg, status);
            actionListener.onFailure(new OpenSearchStatusException(errorMsg, status));
            return;
        }

        // Handle successful status codes with empty body (invalid for most operations)
        if (Strings.isBlank(body) && !action.equals(CANCEL_BATCH_PREDICT.toString())) {
            log.error("Remote model returned successful status {} but with empty response body", statusCode);
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "Remote service returned empty response body",
                        RestStatus.BAD_GATEWAY  // 502 - indicates upstream returned invalid response
                    )
                );
            return;
        }

        try {
            ModelTensors tensors = processOutput(action, body, connector, scriptService, parameters, mlGuard);
            tensors.setStatusCode(statusCode);
            actionListener.onResponse(new Tuple<>(executionContext.getSequence(), tensors));
        } catch (Exception e) {
            log.error("Failed to process response body: {}", body, e);
            actionListener.onFailure(new MLException("Fail to execute " + action + " in aws connector", e));
        }
    }
}
