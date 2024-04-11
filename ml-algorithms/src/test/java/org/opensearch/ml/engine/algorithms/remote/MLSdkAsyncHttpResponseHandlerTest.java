/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
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
    private Map<Integer, ModelTensors> tensorOutputs = new ConcurrentHashMap<>();
    private Connector connector;

    private Connector noProcessFunctionConnector;

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
            .postProcessFunction(MLPostProcessFunction.BEDROCK_EMBEDDING)
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

        ConnectorAction noProcessFunctionPredictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("http://test.com/mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        noProcessFunctionConnector = HttpConnector
            .builder()
            .name("test connector")
            .version("1")
            .protocol("http")
            .actions(Arrays.asList(noProcessFunctionPredictAction))
            .build();
        mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            countDownLatch,
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService,
            null
        );
        responseSubscriber = mlSdkAsyncHttpResponseHandler.new MLResponseSubscriber();
    }

    @Test
    public void test_OnHeaders() {
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        assert mlSdkAsyncHttpResponseHandler.getStatusCode() == 200;
    }

    @Test
    public void test_OnStream_with_postProcessFunction_bedRock() {
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
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        test_OnHeaders(); // set the status code to non-null
        mlSdkAsyncHttpResponseHandler.onStream(stream);
        ArgumentCaptor<List<ModelTensors>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().size() == 1;
        assert captor.getValue().get(0).getMlModelTensors().get(0).getData().length == 8;
    }

    @Test
    public void test_OnStream_without_postProcessFunction() {
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap("{\"key\": \"hello world\"}".getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        MLSdkAsyncHttpResponseHandler noProcessFunctionMlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            countDownLatch,
            actionListener,
            parameters,
            tensorOutputs,
            noProcessFunctionConnector,
            scriptService,
            null
        );
        noProcessFunctionMlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        noProcessFunctionMlSdkAsyncHttpResponseHandler.onStream(stream);
        ArgumentCaptor<List<ModelTensors>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().size() == 1;
        assert captor.getValue().get(0).getMlModelTensors().get(0).getDataAsMap().get("key").equals("hello world");
    }

    @Test
    public void test_onError() {
        test_OnHeaders(); // set the status code to non-null
        mlSdkAsyncHttpResponseHandler.onError(new RuntimeException("runtime exception"));
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assert captor.getValue() instanceof OpenSearchStatusException;
        assert captor.getValue().getMessage().equals("Error on communication with remote model: runtime exception");
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
        SdkHttpFullResponse response = mock(SdkHttpFullResponse.class);
        when(response.statusCode()).thenReturn(500);
        mlSdkAsyncHttpResponseHandler.onHeaders(response);
        responseSubscriber.onError(new Exception("error"));
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof OpenSearchStatusException;
        assert captor.getValue().getMessage().equals("No response from model");
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
        ArgumentCaptor<List<ModelTensors>> captor = ArgumentCaptor.forClass(List.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().size() == 1;
        assert captor.getValue().get(0).getMlModelTensors().get(0).getData().length == 8;
    }

    @Test
    public void test_onComplete_partial_success_exceptionSecond() {
        String response1 = "{\n"
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
        String response2 = "Model current status is: FAILED";
        CountDownLatch count = new CountDownLatch(2);
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler1 = new MLSdkAsyncHttpResponseHandler(
            new WrappedCountDownLatch(0, count),
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService,
            null
        );
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler2 = new MLSdkAsyncHttpResponseHandler(
            new WrappedCountDownLatch(1, count),
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService,
            null
        );
        SdkHttpFullResponse sdkHttpResponse1 = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse1.statusCode()).thenReturn(200);
        mlSdkAsyncHttpResponseHandler1.onHeaders(sdkHttpResponse1);
        Publisher<ByteBuffer> stream1 = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response1.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler1.onStream(stream1);

        SdkHttpFullResponse sdkHttpResponse2 = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse2.statusCode()).thenReturn(500);
        mlSdkAsyncHttpResponseHandler2.onHeaders(sdkHttpResponse2);
        Publisher<ByteBuffer> stream2 = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response2.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler2.onStream(stream2);
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue().getMessage().equals("Error from remote service: Model current status is: FAILED");
        assert captor.getValue().status().getStatus() == 500;
    }

    @Test
    public void test_onComplete_partial_success_exceptionFirst() {
        String response1 = "{\n"
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
        String response2 = "Model current status is: FAILED";
        CountDownLatch count = new CountDownLatch(2);
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler1 = new MLSdkAsyncHttpResponseHandler(
            new WrappedCountDownLatch(0, count),
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService,
            null
        );
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler2 = new MLSdkAsyncHttpResponseHandler(
            new WrappedCountDownLatch(1, count),
            actionListener,
            parameters,
            tensorOutputs,
            connector,
            scriptService,
            null
        );

        SdkHttpFullResponse sdkHttpResponse2 = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse2.statusCode()).thenReturn(500);
        mlSdkAsyncHttpResponseHandler2.onHeaders(sdkHttpResponse2);
        Publisher<ByteBuffer> stream2 = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response2.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler2.onStream(stream2);

        SdkHttpFullResponse sdkHttpResponse1 = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse1.statusCode()).thenReturn(200);
        mlSdkAsyncHttpResponseHandler1.onHeaders(sdkHttpResponse1);
        Publisher<ByteBuffer> stream1 = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(response1.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler1.onStream(stream1);
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue().getMessage().equals("Error from remote service: Model current status is: FAILED");
        assert captor.getValue().status().getStatus() == 500;
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
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue().getMessage().equals("No response from model");
    }

    @Test
    public void test_onComplete_error_http_status() {
        String error = "{\"message\": \"runtime error\"}";
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
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue() instanceof OpenSearchStatusException;
        System.out.println(captor.getValue().getMessage());
        assert captor.getValue().getMessage().contains("runtime error");
    }
}
