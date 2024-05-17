/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.OpenSearchStatusException;
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

    private final ActionListener<List<ModelTensors>> actionListener;

    private final Map<String, String> parameters;

    private final Map<Integer, ModelTensors> tensorOutputs;

    private final Connector connector;

    private final ScriptService scriptService;

    private final MLGuard mlGuard;

    public MLSdkAsyncHttpResponseHandler(
        ExecutionContext executionContext,
        ActionListener<List<ModelTensors>> actionListener,
        Map<String, String> parameters,
        Map<Integer, ModelTensors> tensorOutputs,
        Connector connector,
        ScriptService scriptService,
        MLGuard mlGuard
    ) {
        this.executionContext = executionContext;
        this.actionListener = actionListener;
        this.parameters = parameters;
        this.tensorOutputs = tensorOutputs;
        this.connector = connector;
        this.scriptService = scriptService;
        this.mlGuard = mlGuard;
    }

    @Override
    public void onHeaders(SdkHttpResponse response) {
        SdkHttpFullResponse sdkResponse = (SdkHttpFullResponse) response;
        log.debug("received response headers: " + sdkResponse.headers());
        this.statusCode = sdkResponse.statusCode();
        if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_MULTIPLE_CHOICES) {
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
        log.error(error.getMessage(), error);
        RestStatus status = (statusCode == null) ? RestStatus.INTERNAL_SERVER_ERROR : RestStatus.fromCode(statusCode);
        String errorMessage = "Error communicating with remote model: " + error.getMessage();
        actionListener.onFailure(new OpenSearchStatusException(errorMessage, status));
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
        if (containsThrottlingException && executionContext.getExceptionHolder().get() == null) {
            log.error("Remote server returned error code: {}", statusCode);
            executionContext
                .getExceptionHolder()
                .compareAndSet(
                    null,
                    new OpenSearchStatusException(
                        REMOTE_SERVICE_ERROR + "The request was denied due to remote server throttling.",
                        RestStatus.fromCode(statusCode)
                    )
                );
        }
    }

    private void processResponse(
        Integer statusCode,
        String body,
        Map<String, String> parameters,
        Map<Integer, ModelTensors> tensorOutputs
    ) {
        if (Strings.isBlank(body)) {
            log.error("Remote model response body is empty!");
            if (executionContext.getExceptionHolder().get() == null) {
                executionContext
                    .getExceptionHolder()
                    .compareAndSet(null, new OpenSearchStatusException("No response from model", RestStatus.BAD_REQUEST));
            }
        } else {
            if (statusCode < HttpStatus.SC_OK || statusCode > HttpStatus.SC_MULTIPLE_CHOICES) {
                log.error("Remote server returned error code: {}", statusCode);
                if (executionContext.getExceptionHolder().get() == null) {
                    executionContext
                        .getExceptionHolder()
                        .compareAndSet(null, new OpenSearchStatusException(REMOTE_SERVICE_ERROR + body, RestStatus.fromCode(statusCode)));
                }
            } else {
                try {
                    ModelTensors tensors = processOutput(body, connector, scriptService, parameters, mlGuard);
                    tensors.setStatusCode(statusCode);
                    tensorOutputs.put(executionContext.getSequence(), tensors);
                } catch (Exception e) {
                    log.error("Failed to process response body: {}", body, e);
                    if (executionContext.getExceptionHolder().get() == null) {
                        executionContext
                            .getExceptionHolder()
                            .compareAndSet(null, new MLException("Fail to execute predict in aws connector", e));
                    }
                }
            }
        }
    }

    // Only all requests successful case will be processed here.
    private void reOrderTensorResponses(Map<Integer, ModelTensors> tensorOutputs) {
        ModelTensors[] modelTensors = new ModelTensors[tensorOutputs.size()];
        log.debug("Reordered tensor outputs size is {}", tensorOutputs.size());
        for (Map.Entry<Integer, ModelTensors> entry : tensorOutputs.entrySet()) {
            modelTensors[entry.getKey()] = entry.getValue();
        }
        actionListener.onResponse(Arrays.asList(modelTensors));
    }

    protected class MLResponseSubscriber implements Subscriber<ByteBuffer> {
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription s) {
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
            response(tensorOutputs);
        }

        @Override
        public void onComplete() {
            response(tensorOutputs);
        }
    }

    private void response(Map<Integer, ModelTensors> tensors) {
        processResponse(statusCode, responseBody.toString(), parameters, tensorOutputs);
        executionContext.getCountDownLatch().countDown();
        // when countdown's count equals to 0 means all responses are received.
        if (executionContext.getCountDownLatch().getCount() == 0) {
            if (executionContext.getExceptionHolder().get() != null) {
                actionListener.onFailure(executionContext.getExceptionHolder().get());
                return;
            }
            reOrderTensorResponses(tensors);
        } else {
            log.debug("Not all responses received, left response count is: " + executionContext.getCountDownLatch().getCount());
        }
    }
}
