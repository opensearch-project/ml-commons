/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.engine.algorithms.remote.MLSdkAsyncHttpResponseHandler.AMZ_ERROR_HEADER;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.collect.Tuple;
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
    private final ExecutionContext executionContext = new ExecutionContext(0);
    @Mock
    private ActionListener<Tuple<Integer, ModelTensors>> actionListener;
    @Mock
    private Map<String, String> parameters;
    private Map<Integer, ModelTensors> tensorOutputs = new ConcurrentHashMap<>();
    private Connector connector;

    private Connector noProcessFunctionConnector;

    private Map<String, List<String>> headersMap;

    @Mock
    private SdkHttpFullResponse sdkHttpResponse;
    @Mock
    private ScriptService scriptService;
    private String action;

    private MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler;

    MLSdkAsyncHttpResponseHandler.MLResponseSubscriber responseSubscriber;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(sdkHttpResponse.statusCode()).thenReturn(HttpStatusCode.OK);
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
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
            .actionType(PREDICT)
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
        action = PREDICT.name();
        mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            executionContext,
            actionListener,
            parameters,
            connector,
            scriptService,
            null,
            action
        );
        responseSubscriber = mlSdkAsyncHttpResponseHandler.new MLResponseSubscriber();
        headersMap = Map.of(AMZ_ERROR_HEADER, Arrays.asList("ThrottlingException:request throttled!"));
    }

    @Test
    public void test_OnHeaders() {
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        assert mlSdkAsyncHttpResponseHandler.getStatusCode() == 200;
    }

    @Test
    public void test_OnHeaders_withError() {
        when(sdkHttpResponse.statusCode()).thenReturn(HttpStatusCode.BAD_REQUEST);
        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        assert mlSdkAsyncHttpResponseHandler.getStatusCode() == 400;
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
        ArgumentCaptor<Tuple<Integer, ModelTensors>> captor = ArgumentCaptor.forClass(Tuple.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().v1() == 0;
        assert captor.getValue().v2().getMlModelTensors().get(0).getData().length == 8;
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
            executionContext,
            actionListener,
            parameters,
            noProcessFunctionConnector,
            scriptService,
            null,
            action
        );
        noProcessFunctionMlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        noProcessFunctionMlSdkAsyncHttpResponseHandler.onStream(stream);
        ArgumentCaptor<Tuple<Integer, ModelTensors>> captor = ArgumentCaptor.forClass(Tuple.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().v1() == 0;
        assert captor.getValue().v2().getMlModelTensors().get(0).getDataAsMap().get("key").equals("hello world");
    }

    @Test
    public void test_onError() {
        test_OnHeaders(); // set the status code to non-null
        mlSdkAsyncHttpResponseHandler.onError(new RuntimeException("runtime exception"));
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assert captor.getValue() instanceof OpenSearchStatusException;
        assertEquals("Error communicating with remote model: runtime exception", captor.getValue().getMessage());
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
        assertEquals("hello world", mlSdkAsyncHttpResponseHandler.getResponseBody().toString());
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
        assertEquals("Remote service returned error status 500 with empty body", captor.getValue().getMessage());
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
        ArgumentCaptor<Tuple<Integer, ModelTensors>> captor = ArgumentCaptor.forClass(Tuple.class);
        verify(actionListener).onResponse(captor.capture());
        assert captor.getValue().v1() == 0;
        assert captor.getValue().v2().getMlModelTensors().get(0).getData().length == 8;
    }

    @Test
    public void test_onComplete_failed() {
        String response = "Model current status is: FAILED";
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler1 = new MLSdkAsyncHttpResponseHandler(
            new ExecutionContext(0),
            actionListener,
            parameters,
            connector,
            scriptService,
            null,
            action
        );

        SdkHttpFullResponse sdkHttpResponse = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse.statusCode()).thenReturn(500);
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
        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assertEquals("Error from remote service: Model current status is: FAILED", captor.getValue().getMessage());
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
        assertEquals("Remote service returned empty response body", captor.getValue().getMessage());
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
        assert captor.getValue().getMessage().contains("runtime error");
    }

    @Test
    public void test_onComplete_throttle_error_headers() {
        String error = "{\"message\": null}";
        SdkHttpResponse response = mock(SdkHttpFullResponse.class);
        when(response.statusCode()).thenReturn(HttpStatusCode.BAD_REQUEST);
        when(response.headers()).thenReturn(headersMap);
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
        assert captor.getValue().getMessage().contains(REMOTE_SERVICE_ERROR);
    }

    @Test
    public void test_onComplete_throttle_exception_onFailure() {
        String response = "{\"message\": null}";
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            new ExecutionContext(1),
            actionListener,
            parameters,
            connector,
            scriptService,
            null,
            action
        );

        SdkHttpFullResponse sdkHttpResponse = mock(SdkHttpFullResponse.class);
        when(sdkHttpResponse.statusCode()).thenReturn(HttpStatusCode.BAD_REQUEST);
        when(sdkHttpResponse.headers()).thenReturn(headersMap);
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

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(RemoteConnectorThrottlingException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assert captor.getValue().status().getStatus() == HttpStatusCode.BAD_REQUEST;
        assertEquals(
            "Error from remote service: The request was denied due to remote server throttling. "
                + "To change the retry policy and behavior, please update the connector client_config.",
            captor.getValue().getMessage()
        );
    }

    @Test
    public void test_onComplete_processOutputFail_onFailure() {
        String response = "{\"message\": \"test message\"}";
        Connector testConnector = HttpConnector.builder().name("test connector").version("1").protocol("http").build();
        MLSdkAsyncHttpResponseHandler mlSdkAsyncHttpResponseHandler = new MLSdkAsyncHttpResponseHandler(
            new ExecutionContext(1),
            actionListener,
            parameters,
            testConnector,
            scriptService,
            null,
            action
        );

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

        ArgumentCaptor<IllegalArgumentException> captor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener, times(1)).onFailure(captor.capture());
        assertEquals("no PREDICT action found", captor.getValue().getMessage());
    }

    /**
     * Asserts that IllegalArgumentException is propagated where post-processing function fails
     * on response
     */
    @Test
    public void onComplete_InvalidEmbeddingBedRockPostProcessingOccurs_IllegalArgumentExceptionThrown() {
        String invalidEmbeddingResponse = "{ \"embedding\": [[1]] }";

        mlSdkAsyncHttpResponseHandler.onHeaders(sdkHttpResponse);
        Publisher<ByteBuffer> stream = s -> {
            try {
                s.onSubscribe(mock(Subscription.class));
                s.onNext(ByteBuffer.wrap(invalidEmbeddingResponse.getBytes()));
                s.onComplete();
            } catch (Throwable e) {
                s.onError(e);
            }
        };
        mlSdkAsyncHttpResponseHandler.onStream(stream);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener, times(1)).onFailure(exceptionCaptor.capture());

        // Error message
        assertEquals(
            "BedrockEmbeddingPostProcessFunction exception message should match",
            "The embedding should be a non-empty List containing Float values.",
            exceptionCaptor.getValue().getMessage()
        );
    }
}
