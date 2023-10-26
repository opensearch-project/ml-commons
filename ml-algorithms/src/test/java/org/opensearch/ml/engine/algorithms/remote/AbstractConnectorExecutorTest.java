package org.opensearch.ml.engine.algorithms.remote;

import org.junit.Test;
import org.mockito.Answers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class AbstractConnectorExecutorTest {
    private final AbstractConnectorExecutor connectorExecutor = mock(AbstractConnectorExecutor.class, Answers.CALLS_REAL_METHODS);

    @Test
    public void test_setters() {
        connectorExecutor.setMaxConnections(10);
        connectorExecutor.setReadTimeoutInMillis(1000);
        connectorExecutor.setConnectionTimeoutInMillis(1000);
    }

    @Test
    public void test_getters() {
        connectorExecutor.setMaxConnections(10);
        connectorExecutor.setReadTimeoutInMillis(1000);
        connectorExecutor.setConnectionTimeoutInMillis(1000);
        assertEquals(10L, (long)connectorExecutor.getMaxConnections());
        assertEquals(1000L, (long)connectorExecutor.getReadTimeoutInMillis());
        assertEquals(1000L, (long)connectorExecutor.getConnectionTimeoutInMillis());
    }

    @Test
    public void test_validate() {
        connectorExecutor.setMaxConnections(10);
        connectorExecutor.setReadTimeoutInMillis(1000);
        connectorExecutor.setConnectionTimeoutInMillis(1000);
        connectorExecutor.validate();
    }

    @Test
    public void test_validate_fail() {
        try {
            connectorExecutor.validate();
        } catch (IllegalArgumentException e) {
            assertEquals("connectionTimeoutInMillis must be set to non null value, please check your configuration", e.getMessage());
        }
        connectorExecutor.setConnectionTimeoutInMillis(1000);
        try {
            connectorExecutor.validate();
        } catch (IllegalArgumentException e) {
            assertEquals("readTimeoutInMillis must be set to non null value, please check your configuration", e.getMessage());
        }
        connectorExecutor.setReadTimeoutInMillis(1000);
        try {
            connectorExecutor.validate();
        } catch (IllegalArgumentException e) {
            assertEquals("maxConnections must be set to non null value, please check your configuration", e.getMessage());
        }
    }
}
