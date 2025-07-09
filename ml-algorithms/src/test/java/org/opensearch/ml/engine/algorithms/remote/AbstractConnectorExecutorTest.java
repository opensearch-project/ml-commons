package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

public class AbstractConnectorExecutorTest {
    @Mock
    private AwsConnector mockConnector;

    private ConnectorClientConfig connectorClientConfig;

    private AbstractConnectorExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockConnector.getAccessKey()).thenReturn("access_key");
        when(mockConnector.getSecretKey()).thenReturn("secret_key");
        when(mockConnector.getSessionToken()).thenReturn("session_token");
        when(mockConnector.getRegion()).thenReturn("us-east-1-test");
        executor = new AwsConnectorExecutor(mockConnector);
        connectorClientConfig = new ConnectorClientConfig();
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(null);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeout());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeout());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeout());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeout());
    }
}
