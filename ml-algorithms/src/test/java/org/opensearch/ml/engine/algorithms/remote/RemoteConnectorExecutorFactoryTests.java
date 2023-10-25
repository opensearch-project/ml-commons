package org.opensearch.ml.engine.algorithms.remote;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.REGION_FIELD;
import static org.opensearch.ml.common.connector.HttpConnector.SERVICE_NAME_FIELD;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_CONNECTION_TIMEOUT_IN_MILLI_SECOND;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_MAX_TOTAL_CONNECTIONS;
import static org.opensearch.ml.engine.settings.HttpClientCommonSettings.ML_COMMONS_HTTP_CLIENT_READ_TIMEOUT_IN_MILLI_SECOND;

public class RemoteConnectorExecutorFactoryTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Settings settings = Settings.builder()
        .put(ML_COMMONS_HTTP_CLIENT_MAX_TOTAL_CONNECTIONS.getKey(), 30)
        .put(ML_COMMONS_HTTP_CLIENT_CONNECTION_TIMEOUT_IN_MILLI_SECOND.getKey(), 1000)
        .put(ML_COMMONS_HTTP_CLIENT_READ_TIMEOUT_IN_MILLI_SECOND.getKey(), 1000)
        .build();

    @Test
    public void test_createAWSConnectorExecutor_success() {
        RemoteConnectorExecutorFactory factory = new RemoteConnectorExecutorFactory(settings);
        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        credential.put(REGION_FIELD, "test_region");
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        AwsConnector connector = AwsConnector.awsConnectorBuilder().protocol(ConnectorProtocols.AWS_SIGV4).credential(credential).parameters(parameters).build();
        Assert.assertNotNull(connector);
        RemoteConnectorExecutor executor = factory.create(connector);
        Assert.assertNotNull(executor);
        assertEquals(AwsConnectorExecutor.class, executor.getClass());
    }

    @Test
    public void test_createHttpConnectorExecutor_success() {
        RemoteConnectorExecutorFactory factory = new RemoteConnectorExecutorFactory(settings);
        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_access_key");
        credential.put(SECRET_KEY_FIELD, "test_secret_key");
        Map<String, String> parameters = new HashMap<>();
        parameters.put(SERVICE_NAME_FIELD, "test_service");
        HttpConnector connector = HttpConnector.builder().protocol(ConnectorProtocols.HTTP).credential(credential).parameters(parameters).build();
        Assert.assertNotNull(connector);
        RemoteConnectorExecutor executor = factory.create(connector);
        Assert.assertNotNull(executor);
        assertEquals(HttpJsonConnectorExecutor.class, executor.getClass());
    }

    @Test
    public void test_createConnectorExecutor_typeNotFound() {
        exceptionRule.expect(IllegalArgumentException.class);
        RemoteConnectorExecutorFactory factory = new RemoteConnectorExecutorFactory(settings);
        Connector connector = mock(HttpConnector.class);
        when(connector.getProtocol()).thenReturn("http2");
        RemoteConnectorExecutor executor = factory.create(connector);
        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        assertEquals("Unknown connector type: http2", exceptionCaptor.getValue().getMessage());
    }
}
