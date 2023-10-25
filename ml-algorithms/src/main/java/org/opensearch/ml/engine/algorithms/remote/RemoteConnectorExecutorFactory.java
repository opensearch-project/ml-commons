package org.opensearch.ml.engine.algorithms.remote;


import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.engine.httpclient.MLHttpClientFactory;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.utils.AttributeMap;

import java.time.Duration;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_CONNECTION_TIMEOUT_IN_MILLI_SECOND;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_MAX_TOTAL_CONNECTIONS;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_READ_TIMEOUT_IN_MILLI_SECOND;

public class RemoteConnectorExecutorFactory {

    private final Settings settings;

    public RemoteConnectorExecutorFactory(Settings settings) {
        this.settings = settings;
    }

    public RemoteConnectorExecutor create(Connector connector) {
        switch (connector.getProtocol()) {
            case AWS_SIGV4:
                Duration connectionTimeout = Duration.ofMillis(ML_COMMONS_HTTP_CLIENT_CONNECTION_TIMEOUT_IN_MILLI_SECOND.get(settings));
                Duration readTimeout = Duration.ofMillis(ML_COMMONS_HTTP_CLIENT_READ_TIMEOUT_IN_MILLI_SECOND.get(settings));
                AttributeMap attributeMap = AttributeMap.builder()
                    .put(SdkHttpConfigurationOption.CONNECTION_TIMEOUT, connectionTimeout)
                    .put(SdkHttpConfigurationOption.READ_TIMEOUT, readTimeout)
                    .put(SdkHttpConfigurationOption.MAX_CONNECTIONS, ML_COMMONS_HTTP_CLIENT_MAX_TOTAL_CONNECTIONS.get(settings))
                    .build();
                return new AwsConnectorExecutor(connector, new DefaultSdkHttpClientBuilder().buildWithDefaults(attributeMap));
            case HTTP:
                MLHttpClientFactory httpClientFactory = new MLHttpClientFactory(settings);
                return new HttpJsonConnectorExecutor(connector, httpClientFactory.createHttpClient());
            default:
                throw new IllegalArgumentException("Unknown connector type: " + connector.getProtocol());
        }
    }
}
