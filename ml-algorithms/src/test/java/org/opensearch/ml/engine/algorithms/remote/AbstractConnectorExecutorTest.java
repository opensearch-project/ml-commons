package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.HttpConnector;

public class AbstractConnectorExecutorTest {
    @Mock
    private AwsConnector mockConnector;

    @Mock
    ClusterService clusterService;

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

    @Test
    public void testIsConnectorPrivateIpEnabled() {
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        assertFalse(executor.isConnectorPrivateIpEnabled());
        Settings initialSettings = Settings.builder().put(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED.getKey(), true).build();
        ClusterSettings clusterSettings = new ClusterSettings(
            initialSettings,
            Collections.singleton(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED)
        );
        when(this.clusterService.getClusterSettings()).thenReturn(clusterSettings);
        executor.setClusterService(this.clusterService);
        assertTrue(executor.isConnectorPrivateIpEnabled());
        clusterSettings.applySettings(Settings.builder().put(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED.getKey(), false).build());
        assertFalse(executor.isConnectorPrivateIpEnabled());
    }

    @Test
    public void testIsConnectorPrivateIpEnabled_NullClusterService() {
        Connector connector = HttpConnector.builder().name("test connector").version("1").protocol("http").build();
        HttpJsonConnectorExecutor executor = new HttpJsonConnectorExecutor(connector);
        assertFalse(executor.isConnectorPrivateIpEnabled());
    }
}
