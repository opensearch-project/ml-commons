/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;
import static software.amazon.awssdk.http.SdkHttpMethod.POST;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

@Log4j2
@ConnectorExecutor(AWS_SIGV4)
public class AwsConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private AwsConnector connector;
    private final SdkAsyncHttpClient httpClient;
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

    public AwsConnectorExecutor(Connector connector) {
        this.connector = (AwsConnector) connector;
        this.httpClient = MLHttpClientFactory.getAsyncHttpClient();
    }


    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            String endpoint = connector.getPredictEndpoint(parameters);
            RequestBody requestBody = RequestBody.fromString(payload);

            SdkHttpFullRequest.Builder builder = SdkHttpFullRequest
                .builder()
                .method(POST)
                .uri(URI.create(endpoint))
                .contentStreamProvider(requestBody.contentStreamProvider());
            Map<String, String> headers = connector.getDecryptedHeaders();
            if (headers != null) {
                for (String key : headers.keySet()) {
                    builder.putHeader(key, headers.get(key));
                }
            }
            SdkHttpFullRequest request = builder.build();
            AsyncExecuteRequest executeRequest = AsyncExecuteRequest
                .builder()
                .request(signRequest(request))
                .requestContentPublisher(new SimpleHttpContentPublisher(request))
                .responseHandler(new SdkAsyncHttpResponseHandler() {
                    @Override
                    public void onHeaders(SdkHttpResponse response) {
                        SdkHttpFullResponse sdkResponse = (SdkHttpFullResponse) response;
                        processResponse(sdkResponse, parameters, tensorOutputs);
                    }

                    @Override
                    public void onStream(Publisher<ByteBuffer> stream) {
                        throw new IllegalStateException("Streaming is not supported");
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error(error.getMessage(), error);
                    }
                })
                .build();

            AccessController.doPrivileged((PrivilegedExceptionAction<SdkHttpFullResponse>) () -> {
                 httpClient.execute(executeRequest);
                 return null;
            });


        } catch (RuntimeException exception) {
            log.error("Failed to execute predict in aws connector: " + exception.getMessage(), exception);
            throw exception;
        } catch (Throwable e) {
            log.error("Failed to execute predict in aws connector", e);
            throw new MLException("Fail to execute predict in aws connector", e);
        }
    }

    private void processResponse(SdkHttpFullResponse sdkHttpFullResponse, Map<String, String> parameters, List<ModelTensors> tensorOutputs){
        int statusCode = sdkHttpFullResponse.statusCode();
        Optional<AbortableInputStream> optionalContent = sdkHttpFullResponse.content();
        try (AbortableInputStream body = optionalContent.orElse(null)) {
            if (body == null) {
                throw new OpenSearchStatusException("No response from model", RestStatus.fromCode(statusCode));
            } else {
                String response = IOUtils.toString(body, StandardCharsets.UTF_8);
                if (statusCode < 200 || statusCode > 300) {
                    throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + response, RestStatus.fromCode(statusCode));
                } else {
                    ModelTensors tensors = processOutput(response, connector, scriptService, parameters);
                    tensors.setStatusCode(statusCode);
                    tensorOutputs.add(tensors);
                }
            }
        } catch (IOException e) {
            log.error("IOException while parsing response from model", e);
            throw new OpenSearchStatusException("Error parsing response from model", RestStatus.fromCode(statusCode));
        }
    }

    private SdkHttpFullRequest signRequest(SdkHttpFullRequest request) {
        String accessKey = connector.getAccessKey();
        String secretKey = connector.getSecretKey();
        String sessionToken = connector.getSessionToken();
        String signingName = connector.getServiceName();
        String region = connector.getRegion();

        return ConnectorUtils.signRequest(request, accessKey, secretKey, sessionToken, signingName, region);
    }
}
