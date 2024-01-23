/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

@Log4j2
public class MLSdkAsyncHttpResponseHandler implements SdkAsyncHttpResponseHandler {
    private Integer statusCode;
    private final StringBuilder responseBody = new StringBuilder();

    private CountDownLatch countDownLatch;

    private ActionListener<Queue<ModelTensors>> actionListener;

    private Map<String, String> parameters;

    private Queue<ModelTensors> tensorOutputs;

    private Connector connector;

    private ScriptService scriptService;

    private Subscription subscription;

    public MLSdkAsyncHttpResponseHandler(
        CountDownLatch countDownLatch,
        ActionListener<Queue<ModelTensors>> actionListener,
        Map<String, String> parameters,
        Queue<ModelTensors> tensorOutputs,
        Connector connector,
        ScriptService scriptService
    ) {
        this.countDownLatch = countDownLatch;
        this.actionListener = actionListener;
        this.parameters = parameters;
        this.tensorOutputs = tensorOutputs;
        this.connector = connector;
        this.scriptService = scriptService;
    }

    @Override
    public void onHeaders(SdkHttpResponse response) {
        SdkHttpFullResponse sdkResponse = (SdkHttpFullResponse) response;
        log.debug("received response headers: " + sdkResponse.headers());
        this.statusCode = sdkResponse.statusCode();
    }

    @Override
    public void onStream(Publisher<ByteBuffer> stream) {
        stream.subscribe(new Subscriber<>() {
            private Subscription subscription;
            @Override public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer byteBuffer) {
                responseBody.append(StandardCharsets.UTF_8.decode(byteBuffer));
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onError(Throwable t) {
                log.error("Error on receiving response from remote: {}", t.getMessage(), t);
                actionListener.onFailure(new OpenSearchStatusException("Error on receiving response from remote: " + t.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
            }

            @Override
            public void onComplete() {
                String fullResponseBody = responseBody.toString();
                try {
                    processResponse(statusCode, fullResponseBody, parameters, tensorOutputs);
                    countDownLatch.countDown();
                    if (countDownLatch.getCount() == 0) {
                        log.debug("All responses received, calling action listener to return final results.");
                        actionListener.onResponse(tensorOutputs);
                    }
                } catch (IOException e) {
                    log.error("Error on processing response from remote: {}", e.getMessage(), e);
                    actionListener.onFailure(new OpenSearchStatusException("Error on processing response from remote: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
                }
            }
        });
    }

    @Override
    public void onError(Throwable error) {
        log.error(error.getMessage(), error);
        actionListener.onFailure(new OpenSearchStatusException("Error receiving response from remote: " + error.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
    }

    private void processResponse(Integer statusCode, String body, Map<String, String> parameters, Queue<ModelTensors> tensorOutputs)
        throws IOException {
        if (body == null) {
            throw new OpenSearchStatusException("No response from model", RestStatus.fromCode(statusCode));
        } else {
            if (statusCode < 200 || statusCode > 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + body, RestStatus.fromCode(statusCode));
            } else {
                ModelTensors tensors = processOutput(body, connector, scriptService, parameters);
                tensors.setStatusCode(statusCode);
                tensorOutputs.add(tensors);
            }
        }
    }
}
