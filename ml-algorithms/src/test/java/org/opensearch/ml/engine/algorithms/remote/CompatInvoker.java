/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.MockMakers;
import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.httpclient.MLHttpClientFactory;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.compat.CompatEnvironment;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

/**
 * Drives a stored remote model through the real deploy-and-predict path - {@link RemoteModel#initModelAsync}
 * (credential decrypt, executor selection) and {@link RemoteConnectorExecutor#executeAction} (payload build,
 * endpoint resolution and trusted-endpoint check, header substitution, request signing, response
 * post-processing) - with the network replaced by a fake {@link SdkAsyncHttpClient} that records the request
 * and answers with a canned body. Anything else predict reaches out to is stubbed by the
 * {@link CompatEnvironment}.
 *
 * <p>Lives in this package because {@code RemoteModel#getConnectorExecutor} is a package-private test hook.
 */
public final class CompatInvoker {

    /** Static mocks need the inline mock maker, whatever the module's default mock maker is. */
    private static final MockSettings INLINE = withSettings().mockMaker(MockMakers.INLINE);

    public static final String CLUSTER_NAME = "compat-cluster";

    /** What the fake HTTP client received. */
    public static final class SentRequest {
        public final String method;
        public final String uri;
        public final Map<String, List<String>> headers;
        public final String body;

        SentRequest(SdkHttpRequest request, String body) {
            this.method = request.method().name();
            this.uri = request.getUri().toString();
            this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            this.headers.putAll(request.headers());
            this.body = body;
        }

        public Optional<String> header(String name) {
            List<String> v = headers.get(name);
            return v == null || v.isEmpty() ? Optional.empty() : Optional.of(v.get(0));
        }
    }

    public static final class Result {
        public final SentRequest request;
        public final ModelTensorOutput output;

        Result(SentRequest request, ModelTensorOutput output) {
            this.request = request;
            this.output = output;
        }
    }

    private CompatInvoker() {}

    /**
     * @param model        the stored model with its connector already attached (inline, or resolved from connector_id)
     * @param masterKey    master key of the domain that stored the model
     * @param input        predict input
     * @param responseBody body the fake remote endpoint answers with (HTTP 200)
     * @param environment  distribution-specific stubs and settings
     */
    public static Result invoke(MLModel model, String masterKey, MLInput input, String responseBody, CompatEnvironment environment)
        throws Exception {
        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.EMPTY));
        // Response handling may be offloaded to the predict thread pool (ThreadedActionListener); run it inline.
        when(threadPool.executor(anyString())).thenReturn(OpenSearchExecutors.newDirectExecutorService());
        Client client = mock(Client.class);
        when(client.threadPool()).thenReturn(threadPool);
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getClusterName()).thenReturn(new ClusterName(CLUSTER_NAME));
        SdkClient sdkClient = mock(SdkClient.class);
        when(sdkClient.isGlobalResource(anyString(), any())).thenReturn(CompletableFuture.completedFuture(false));

        Map<String, Object> params = new HashMap<>();
        params.put(RemoteModel.SDK_CLIENT, sdkClient);
        params.put(RemoteModel.CLIENT, client);
        params.put(RemoteModel.CLUSTER_SERVICE, clusterService);
        params.put(RemoteModel.SETTINGS, Settings.EMPTY);
        params.put(RemoteModel.TRUSTED_CONNECTOR_ENDPOINTS_REGEX, environment.predictTrustedEndpoints());

        FakeHttpClient http = new FakeHttpClient(responseBody);
        RemoteModel remoteModel = new RemoteModel();
        // Stubs (e.g. Mockito static mocks) are thread-local; the whole path below runs synchronously on this thread.
        // Every executor builds its HTTP client through MLHttpClientFactory.getAsyncHttpClient (directly, or via a client
        // cache), so intercepting that hands the fake to all of them and nothing can reach the network.
        try (
            AutoCloseable stubs = environment.stubExternalServices(model);
            // Every client factory overload (the parameter list differs between releases) answers with the fake client.
            // Void members are host validators that resolve the connector host over DNS (private-IP check on some
            // releases); they are skipped so the test is hermetic - endpoint trust is checked by validate-endpoint.
            MockedStatic<MLHttpClientFactory> clients = mockStatic(
                MLHttpClientFactory.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(invocation -> {
                    Class<?> returnType = invocation.getMethod().getReturnType();
                    if (SdkAsyncHttpClient.class.isAssignableFrom(returnType)) {
                        return http;
                    }
                    if (returnType == void.class) {
                        return null;
                    }
                    return invocation.callRealMethod();
                })
            )
        ) {
            CompletableFuture<Object> deployed = new CompletableFuture<>();
            remoteModel
                .initModelAsync(
                    model,
                    params,
                    new EncryptorImpl(null, masterKey),
                    ActionListener.wrap(deployed::complete, deployed::completeExceptionally)
                );
            deployed.get(30, TimeUnit.SECONDS);

            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            executor = environment.prepareExecutor(executor, model);

            CompletableFuture<MLTaskResponse> predicted = new CompletableFuture<>();
            executor.executeAction("PREDICT", input, ActionListener.wrap(predicted::complete, predicted::completeExceptionally));
            MLOutput output = predicted.get(30, TimeUnit.SECONDS).getOutput();
            if (http.sent.get() == null) {
                throw new IllegalStateException("predict completed without sending a request through the fake HTTP client");
            }
            return new Result(http.sent.get(), (ModelTensorOutput) output);
        } finally {
            remoteModel.close();
        }
    }

    /** Decrypts the connector's stored credentials with {@code masterKey} the way deploy does; returns them in clear. */
    public static Map<String, String> decryptCredentials(Connector connector, String masterKey) throws Exception {
        EncryptorImpl encryptor = new EncryptorImpl(null, masterKey);
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        connector
            .decrypt(firstAction(connector), encryptor::decrypt, null, ActionListener.wrap(done::complete, done::completeExceptionally));
        done.get(30, TimeUnit.SECONDS);
        return ((AbstractConnector) connector).getDecryptedCredential();
    }

    private static String firstAction(Connector connector) {
        List<ConnectorAction> actions = connector.getActions();
        return actions == null || actions.isEmpty() ? "PREDICT" : actions.get(0).getActionType().name();
    }

    /** Records the request and replies 200 with a fixed body, synchronously on the calling thread. */
    static final class FakeHttpClient implements SdkAsyncHttpClient {
        final AtomicReference<SentRequest> sent = new AtomicReference<>();
        private final byte[] responseBody;

        FakeHttpClient(String responseBody) {
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
            sent.set(new SentRequest(request.request(), readBody(request)));
            request
                .responseHandler()
                .onHeaders(SdkHttpFullResponse.builder().statusCode(200).putHeader("Content-Type", "application/json").build());
            request.responseHandler().onStream(new OneShotPublisher(responseBody));
            return CompletableFuture.completedFuture(null);
        }

        private static String readBody(AsyncExecuteRequest request) {
            if (request.requestContentPublisher() == null) {
                return "";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Throwable> errors = new ArrayList<>();
            request.requestContentPublisher().subscribe(new Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer b) {
                    byte[] bytes = new byte[b.remaining()];
                    b.get(bytes);
                    out.write(bytes, 0, bytes.length);
                }

                @Override
                public void onError(Throwable t) {
                    errors.add(t);
                }

                @Override
                public void onComplete() {}
            });
            if (!errors.isEmpty()) {
                throw new IllegalStateException("failed to read request body", errors.get(0));
            }
            return out.toString(StandardCharsets.UTF_8);
        }

        @Override
        public String clientName() {
            return "ml-compat-fake";
        }

        @Override
        public void close() {}
    }

    static final class OneShotPublisher implements org.reactivestreams.Publisher<ByteBuffer> {
        private final byte[] body;

        OneShotPublisher(byte[] body) {
            this.body = body;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private boolean done;

                @Override
                public void request(long n) {
                    if (done) {
                        return;
                    }
                    done = true;
                    subscriber.onNext(ByteBuffer.wrap(body));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
