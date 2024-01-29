/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.SdkHttpFullResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class MLSdkAsyncHttpResponseHandlerTest {

    private WrappedCountDownLatch countDownLatch = new WrappedCountDownLatch(0, new CountDownLatch(1));
@Mock
    private ActionListener<List<ModelTensors>> actionListener;
@Mock
    private Map<String, String> parameters;
@Mock
    private Map<Integer, ModelTensors> tensorOutputs;
@Mock
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
        mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(countDownLatch, actionListener, parameters, tensorOutputs, connector, scriptService);
        when(sdkHttpResponse.statusCode()).thenReturn(HttpStatusCode.OK);
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
    public void test_onComplete() throws IOException {
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
        mockStatic(ConnectorUtils.class);
        ModelTensors modelTensors = mock(ModelTensors.class);
        when(ConnectorUtils.processOutput(mlSdkAsyncHttpResponseHandler.getResponseBody().toString(), connector, scriptService, parameters)).thenReturn(modelTensors);
        when(modelTensors.getStatusCode()).thenReturn(200);
        responseSubscriber.onComplete();
    }
}
