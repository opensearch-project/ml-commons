package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.ConnectorHttpClientConfig;

public class AbstractConnectorExecutorTest {
    @Mock
    private AwsConnector mockConnector;

    @Mock
    private ConnectorHttpClientConfig mockConfig;

    private AbstractConnectorExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        executor = new AwsConnectorExecutor(mockConnector);
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getHttpClientConfig()).thenReturn(null);

        executor.validate(mockConnector);

        assertEquals(ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getMaxConnections());
        assertEquals(ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectionTimeoutInMillis());
        assertEquals(ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getReadTimeoutInMillis());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getHttpClientConfig()).thenReturn(mockConfig);
        when(mockConfig.getMaxConnections()).thenReturn(null);
        when(mockConfig.getConnectionTimeout()).thenReturn(null);
        when(mockConfig.getReadTimeout()).thenReturn(null);

        executor.validate(mockConnector);

        assertEquals(ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getMaxConnections());
        assertEquals(ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectionTimeoutInMillis());
        assertEquals(ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getReadTimeoutInMillis());
    }
}
