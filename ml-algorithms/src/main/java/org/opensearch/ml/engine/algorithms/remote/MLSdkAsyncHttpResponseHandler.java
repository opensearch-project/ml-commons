/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.util.Strings;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
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
    @Getter
    private Integer statusCode;
    @Getter
    private final StringBuilder responseBody = new StringBuilder();

    private WrappedCountDownLatch countDownLatch;

    private ActionListener<List<ModelTensors>> actionListener;

    private Map<String, String> parameters;

    private Map<Integer, ModelTensors> tensorOutputs;

    private Connector connector;

    private ScriptService scriptService;

    private final List<String> errorMsg = new ArrayList<>();

    public MLSdkAsyncHttpResponseHandler(
        WrappedCountDownLatch countDownLatch,
        ActionListener<List<ModelTensors>> actionListener,
        Map<String, String> parameters,
        Map<Integer, ModelTensors> tensorOutputs,
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
        stream.subscribe(new MLResponseSubscriber());
    }

    @Override
    public void onError(Throwable error) {
        log.error(error.getMessage(), error);
        actionListener
            .onFailure(
                new OpenSearchStatusException(
                    "Error on communication with remote model: " + error.getMessage(),
                    RestStatus.INTERNAL_SERVER_ERROR
                )
            );
    }

    private void processResponse(Integer statusCode, String body, Map<String, String> parameters, Map<Integer, ModelTensors> tensorOutputs)
        throws IOException {
        if (Strings.isBlank(body)) {
            log.error("Remote model response body is empty!");
            throw new OpenSearchStatusException("Remote model response is empty", RestStatus.fromCode(statusCode));
        } else {
            if (statusCode < 200 || statusCode > 300) {
                log.error("Remote server returned error code: {}", statusCode);
                throw new OpenSearchStatusException("Remote server returned error code: " + statusCode, RestStatus.fromCode(statusCode));
            } else {
                ModelTensors tensors = processOutput(body, connector, scriptService, parameters);
                tensors.setStatusCode(statusCode);
                tensorOutputs.put(countDownLatch.getSequence(), tensors);
            }
        }
    }

    private List<ModelTensors> reOrderTensorResponses(Map<Integer, ModelTensors> tensorOutputs) {
        List<ModelTensors> modelTensors = new ArrayList<>();
        TreeMap<Integer, ModelTensors> sortedMap = new TreeMap<>(tensorOutputs);
        log.info("Reordered tensor outputs size is {}", sortedMap.size());
        for (Map.Entry<Integer, ModelTensors> entry : sortedMap.entrySet()) {
            modelTensors.add(entry.getKey(), entry.getValue());
        }
        return modelTensors;
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
            countDownLatch.getCountDownLatch().countDown();
            log
                .error(
                    "Error on receiving response body from remote: {}",
                    t instanceof NullPointerException ? "NullPointerException" : t.getMessage(),
                    t
                );
            errorMsg
                .add(
                    "Error on receiving response body from remote: "
                        + (t instanceof NullPointerException ? "NullPointerException" : t.getMessage())
                );
            if (countDownLatch.getCountDownLatch().getCount() == 0) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Error on receiving response body from remote: " + String.join(",", errorMsg),
                            RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
            } else {
                log.debug("Not all responses received, left response count is: " + countDownLatch.getCountDownLatch().getCount());
            }
        }

        @Override
        public void onComplete() {
            try {
                String fullResponseBody = responseBody.toString();
                processResponse(statusCode, fullResponseBody, parameters, tensorOutputs);
                countDownLatch.getCountDownLatch().countDown();
                if (countDownLatch.getCountDownLatch().getCount() == 0) {
                    log.debug("All responses received, calling action listener to return final results.");
                    actionListener.onResponse(reOrderTensorResponses(tensorOutputs));
                }
            } catch (Throwable e) {
                countDownLatch.getCountDownLatch().countDown();
                log
                    .error(
                        "Error on processing response from remote: {}",
                        e instanceof NullPointerException ? "NullPointerException" : e.getMessage(),
                        e
                    );
                errorMsg
                    .add(
                        "Error on receiving response from remote: "
                            + (e instanceof NullPointerException ? "NullPointerException" : e.getMessage())
                    );
                if (countDownLatch.getCountDownLatch().getCount() == 0) {
                    actionListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Error on receiving response from remote: " + String.join(",", errorMsg),
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                } else {
                    log.debug("Not all responses received, left response count is: " + countDownLatch.getCountDownLatch().getCount());
                }
            }
        }
    }
}
