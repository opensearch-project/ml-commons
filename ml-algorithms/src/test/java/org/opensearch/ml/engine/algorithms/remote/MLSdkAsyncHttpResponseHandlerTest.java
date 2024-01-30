/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;

public class MLSdkAsyncHttpResponseHandlerTest {

    private final WrappedCountDownLatch countDownLatch = new WrappedCountDownLatch(0, new CountDownLatch(1));
    @Mock
    private ActionListener<List<ModelTensors>> actionListener;
    @Mock
    private Map<String, String> parameters;
    @Mock
    private Map<Integer, ModelTensors> tensorOutputs;
    private Connector connector;

    @Mock
    private SdkHttpFullResponse sdkHttpResponse;
    @Mock
    private ScriptService scriptService;

    private MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler;

    MLSdkAsyncHttpResponseHandler.MLResponseSubscriber responseSubscriber;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(sdkHttpResponse.statusCode()).thenReturn(HttpStatusCode.OK);
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        connector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(predictAction))
            .build();
        mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            countDownLatch,
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService
        );
        responseSubscriber = mlSdkAsyncHttpResponseHandler.new MLResponseSubscriber();
    }

    @Test
    public void test_OnHeaders() {
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        assert mlSdkAsyncHttpResponseHandler.getStatusCode() == 200;
    }

    @Test
    public void test_OnStream() {
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap("hello world".getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        test_OnHeaders(); // set the status code to non-null
        mlSdkAsyncHttpResponseHandler.onStream(stream);
    }

    @Test
    public void test_MLSdkAsyncHttpResponseHandler_onError() {
        mlSdkAsyncHttpResponseHandler.onError(new Exception("error"));
    }

    @Test
    public void test_onSubscribe() {
        Subscription subscription = mock(Subscription.class);
        responseSubscriber.onSubscribe(subscription);
    }

    @Test
    public void test_onNext() {
        test_onSubscribe();// set the subscription to non-null.
        responseSubscriber.onNext(ByteBuffer.wrap("hello world".getBytes()));
        assert mlSdkAsyncHttpResponseHandler.getResponseBody().toString().equals("hello world");
    }

    @Test
    public void test_MLResponseSubscriber_onError() {
        responseSubscriber.onError(new Exception("error"));
    }

    @Test
    public void test_onComplete_success() {
        String response = "{\n"
            + "    \"embedding\": [\n"
            + "        0.46484375,\n"
            + "        -0.017822266,\n"
            + "        0.17382812,\n"
            + "        0.10595703,\n"
            + "        0.875,\n"
            + "        0.19140625,\n"
            + "        -0.36914062,\n"
            + "        -0.0011978149\n"
            + "    ]\n"
            + "}";
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler.onStream(stream);
        responseSubscriber.onComplete();
    }

    @Test
    public void test_onComplete_empty_response_body() {
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap("".getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler.onStream(stream);
        responseSubscriber.onComplete();
    }

    @Test
    public void test_onComplete_error_http_status() {
        String error = "{\n" + "    \"message\": \"runtime error\"\n" + "}";
        SdkHttpResponse response = mock(SdkHttpFullResponse.class);
        when(response.statusCode()).thenReturn(HttpStatusCode.INTERNAL_SERVER_ERROR);
        mlSdkAsyncHttpResponseHandler.onHeaders(response);
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(error.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler.onStream(stream);
        responseSubscriber.onComplete();
    }

    @Test
    public void test_onComplete_error() {
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap("hello world".getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler.onStream(stream);
        responseSubscriber.onComplete();
    }
}
