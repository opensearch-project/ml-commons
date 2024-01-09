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

    private ConnectorHttpClientConfig httpClientConfig;

    private AbstractConnectorExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        executor = new AwsConnectorExecutor(mockConnector);
        httpClientConfig = new ConnectorHttpClientConfig();
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getHttpClientConfig()).thenReturn(null);
        executor.initialize(mockConnector);
        assertEquals(ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getHttpClientConfig().getMaxConnections());
        assertEquals(ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getHttpClientConfig().getConnectionTimeout());
        assertEquals(ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getHttpClientConfig().getReadTimeout());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getHttpClientConfig()).thenReturn(httpClientConfig);
        executor.initialize(mockConnector);
        assertEquals(ConnectorHttpClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getHttpClientConfig().getMaxConnections());
        assertEquals(ConnectorHttpClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getHttpClientConfig().getConnectionTimeout());
        assertEquals(ConnectorHttpClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getHttpClientConfig().getReadTimeout());
    }
}
