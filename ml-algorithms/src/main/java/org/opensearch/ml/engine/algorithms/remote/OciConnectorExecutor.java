/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;
import static org.opensearch.ml.common.connector.ConnectorProtocols.OCI_SIGV1;
import static org.opensearch.ml.common.connector.OciConnector.OciClientAuthType;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processOutput;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.OciConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
import org.opensearch.script.ScriptService;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.http.internal.RestClient;
import com.oracle.bmc.http.internal.RestClientFactory;
import com.oracle.bmc.http.internal.RestClientFactoryBuilder;
import com.oracle.bmc.http.internal.WrappedInvocationBuilder;
import com.oracle.bmc.http.signing.DefaultRequestSigner;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.requests.BmcRequest;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j2;

/**
 * OciConnectorExecutor is responsible to call remote model from OCI services
 */
@Log4j2
@ConnectorExecutor(OCI_SIGV1)
public class OciConnectorExecutor implements RemoteConnectorExecutor {

    @Getter
    private final OciConnector connector;

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
    private org.opensearch.client.Client client;

    private final RestClient restClient;

    public OciConnectorExecutor(Connector connector) {
        this.connector = (OciConnector) connector;

        final BasicAuthenticationDetailsProvider provider = buildAuthenticationDetailsProvider(connector);
        final RestClientFactory restClientFactory = RestClientFactoryBuilder.builder().build();
        final RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(provider);
        this.restClient = restClientFactory.create(requestSigner, Collections.emptyMap());
    }

    @Override
    public void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs) {
        try {
            final String endpoint = connector.getPredictEndpoint(parameters);
            final String method = connector.getPredictHttpMethod();
            final Response response = makeHttpCall(endpoint, method, payload);

            final String modelResponse = getInputStreamContent((InputStream) response.getEntity());
            final int statusCode = response.getStatus();
            if (statusCode < 200 || statusCode >= 300) {
                throw new OpenSearchStatusException(REMOTE_SERVICE_ERROR + modelResponse, RestStatus.fromCode(statusCode));
            }

            final ModelTensors tensors = processOutput(modelResponse, connector, scriptService, parameters);
            tensors.setStatusCode(statusCode);
            tensorOutputs.add(tensors);
        } catch (IOException e) {
            throw new MLException("Fail to execute predict in oci connector", e);
        }
    }

    private Response makeHttpCall(String endpoint, String httpMethod, String payload) {
        final WebTarget target = getWebTarget(endpoint);
        final WrappedInvocationBuilder wrappedIb = new WrappedInvocationBuilder(target.request(), target.getUri());
        final Response response;
        switch (httpMethod.toUpperCase(Locale.ROOT)) {
            case "POST":
                response = restClient.post(wrappedIb, payload, new BmcRequest<>());
                break;
            case "GET":
                response = restClient.get(wrappedIb, new BmcRequest<>());
                break;
            default:
                throw new IllegalArgumentException("unsupported http method");
        }
        return response;
    }

    /**
     *
     * RestClient is a general wrapper for the {@link Client}, designed to communicate with OCI services.
     * However, it suffers from a poor design, especially regarding support for calling multiple endpoints.
     * Specifically, when RestClient::setEndpoint is called, it creates a new instance of WebTarget with
     * that endpoint. But instead of returning this instance directly, it requires the customer to make an
     * additional call to access the WebTarget instance.
     *
     * <p>For now, let's make the calls to setEndpoint and getBaseTarget synchronized to avoid race conditions.
     * Moreover, this approach is relatively inexpensive compared to making remote endpoint calls.
     *
     * @param endpoint the web target endpoint
     * @return web target for particular endpoint
     */
    @Synchronized
    private WebTarget getWebTarget(String endpoint) {
        restClient.setEndpoint(endpoint);
        return restClient.getBaseTarget();
    }

    private static BasicAuthenticationDetailsProvider buildAuthenticationDetailsProvider(Connector connector) {
        final Map<String, String> parameters = connector.getParameters();
        final OciClientAuthType authType = OciClientAuthType.from(parameters.get(OciConnector.AUTH_TYPE_FIELD).toUpperCase(Locale.ROOT));

        switch (authType) {
            case RESOURCE_PRINCIPAL:
                return ResourcePrincipalAuthenticationDetailsProvider.builder().build();
            case INSTANCE_PRINCIPAL:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            case USER_PRINCIPAL:
                return SimpleAuthenticationDetailsProvider
                    .builder()
                    .tenantId(parameters.get(OciConnector.TENANT_ID_FIELD))
                    .userId(parameters.get(OciConnector.USER_ID_FIELD))
                    .region(Region.fromRegionCodeOrId(parameters.get(OciConnector.REGION_FIELD)))
                    .fingerprint(parameters.get(OciConnector.FINGERPRINT_FIELD))
                    .privateKeySupplier(() -> {
                        try {
                            return new FileInputStream(parameters.get(OciConnector.PEMFILE_PATH_FIELD));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read private key", e);
                        }
                    })
                    .build();
            default:
                throw new IllegalArgumentException("OCI client auth type is not supported " + authType);
        }
    }

    private static String getInputStreamContent(InputStream in) throws IOException {
        final StringBuilder body = new StringBuilder();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        return body.toString();
    }
}
