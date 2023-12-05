/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.ml.engine.credentialscommunication.SecretManagerCredentials;
import org.opensearch.ml.engine.credentialscommunication.SecretsManager;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import org.opensearch.script.ScriptService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ConnectorExecutor(HTTP)
public class HttpJsonConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private HttpConnector connector;
    @Setter
    @Getter
    private ScriptService scriptService;

    @Setter
    @Getter
    private ClusterService clusterService;

    public HttpJsonConnectorExecutor(Connector connector) {
        this.connector = (HttpConnector) connector;
    }

    @Override
    public void invokeRemoteModelInManagedService(
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        List<ModelTensors> tensorOutputs
    ) {
        try {
            AtomicReference<String> responseRef = new AtomicReference<>("");
            AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
            HttpUriRequest request;
            switch (connector.getPredictHttpMethod().toUpperCase(Locale.ROOT)) {
                case "POST":
                    try {
                        String predictEndpoint = connector.getPredictEndpoint(parameters);
                        request = new HttpPost(predictEndpoint);
                        HttpEntity entity = new StringEntity(payload);
                        ((HttpPost) request).setEntity(entity);
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                case "GET":
                    try {
                        request = new HttpGet(connector.getPredictEndpoint(parameters));
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            Map<String, String> headers = connector.getDecryptedHeaders();

            Map<String, String> secretManagerCredentials = new HashMap<>();

            boolean hasContentTypeHeader = false;
            String secretArnPrefix = "${credential.secretArn.";
            String secretArnSuffix = "}";
            String regex = Pattern.quote(secretArnPrefix) + "(.*?)" + Pattern.quote(secretArnSuffix);

            if (headers != null) {
                for (String key : headers.keySet()) {
                    if (headers.get(key).contains(secretArnPrefix)) {
                        List<String> matches = new ArrayList<>();
                        Matcher matcher = Pattern.compile(regex).matcher(headers.get(key));
                        while (matcher.find()) {
                            String match = matcher.group(1); // This is the text between the prefix and suffix
                            matches.add(match);
                        }
                        for (String match : matches) {
                            secretManagerCredentials.put(match, "");
                        }
                    } else {
                        request.addHeader(key, headers.get(key));
                    }
                }
            }

            if (!secretManagerCredentials.entrySet().isEmpty()) {
                String clusterName = clusterService.getClusterName().toString();
                String roleArn = connector.getDecryptedCredential().get("roleArn");
                String secretArn = connector.getDecryptedCredential().get("secretArn");

                SecretManagerCredentials secretManagerCredentialsRequest = new SecretManagerCredentials(roleArn, clusterName, secretArn);
                JsonObject secretManagerResponse = SecretsManager.getSecretValue(secretManagerCredentialsRequest);
                for (String key : secretManagerCredentials.keySet()) {
                    JsonElement secretValue = secretManagerResponse.get(key);
                    String secretVal = secretValue.isJsonNull() ? "" : secretValue.getAsString();
                    secretManagerCredentials.put(key, secretVal);
                }

            }

            StringSubstitutor substitutor = new StringSubstitutor(secretManagerCredentials, "${credential.secretArn.", "}");
            if (headers != null) {
                for (String key : headers.keySet()) {
                    headers.put(key, substitutor.replace(headers.get(key)));
                    if (!request.containsHeader(key)) {
                        request.addHeader(key, substitutor.replace(headers.get(key)));
                    }
                }
            }

            if (!hasContentTypeHeader) {
                request.addHeader("Content-Type", "application/json");
            }

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                try (CloseableHttpClient httpClient = getHttpClient(); CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity responseEntity = response.getEntity();
                    String responseBody = EntityUtils.toString(responseEntity);
                    EntityUtils.consume(responseEntity);
                    responseRef.set(responseBody);
                    statusCodeRef.set(response.getStatusLine().getStatusCode());
                }
                return null;
            });
            String modelResponse = responseRef.get();
            Integer statusCode = statusCodeRef.get();
            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector in managed service", e);
            throw e;
        } catch (Throwable e) {
            log.error("Fail to execute http connector in managed service", e);
            throw new MLException("Fail to execute http connector in managed service", e);
        }
    }

    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            AtomicReference<String> responseRef = new AtomicReference<>("");
            AtomicReference<Integer> statusCodeRef = new AtomicReference<>();

            HttpUriRequest request;
            switch (connector.getPredictHttpMethod().toUpperCase(Locale.ROOT)) {
                case "POST":
                    try {
                        String predictEndpoint = connector.getPredictEndpoint(parameters);
                        request = new HttpPost(predictEndpoint);
                        String charset = parameters.containsKey("charset") ? parameters.get("charset") : "UTF-8";
                        HttpEntity entity = new StringEntity(payload, charset);
                        ((HttpPost) request).setEntity(entity);
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                case "GET":
                    try {
                        request = new HttpGet(connector.getPredictEndpoint(parameters));
                    } catch (Exception e) {
                        throw new MLException("Failed to create http request for remote model", e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unsupported http method");
            }

            Map<String, ?> headers = connector.getDecryptedHeaders();
            boolean hasContentTypeHeader = false;
            if (headers != null) {
                for (String key : headers.keySet()) {
                    request.addHeader(key, (String) headers.get(key));
                    if (key.toLowerCase().equals("Content-Type")) {
                        hasContentTypeHeader = true;
                    }
                }
            }
            if (!hasContentTypeHeader) {
                request.addHeader("Content-Type", "application/json");
            }

            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                try (CloseableHttpClient httpClient = getHttpClient(); CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity responseEntity = response.getEntity();
                    String responseBody = EntityUtils.toString(responseEntity);
                    EntityUtils.consume(responseEntity);
                    responseRef.set(responseBody);
                    statusCodeRef.set(response.getStatusLine().getStatusCode());
                }
                return null;
            });
            String modelResponse = responseRef.get();
            Integer statusCode = statusCodeRef.get();
            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (RuntimeException e) {
            log.error("Fail to execute http connector", e);
            throw e;
        } catch (Throwable e) {
            log.error("Fail to execute http connector", e);
            throw new MLException("Fail to execute http connector", e);
        }
    }

    public CloseableHttpClient getHttpClient() {
        return MLHttpClientFactory.getCloseableHttpClient();
    }
}
