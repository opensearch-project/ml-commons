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
        executor = new AwsConnectorExecutor(mockConnector);
        connectorClientConfig = new ConnectorClientConfig();
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(null);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeoutMillis());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeoutSeconds());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeoutMillis());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeoutSeconds());
    }
}
