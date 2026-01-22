/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.engine.algorithms.contextmanager.SlidingWindowManager;
import org.opensearch.ml.engine.algorithms.contextmanager.SummarizationManager;
import org.opensearch.ml.engine.algorithms.contextmanager.ToolsOutputTruncateManager;
import org.opensearch.transport.client.Client;

public class ContextManagerFactoryTests {

    private ContextManagerFactory contextManagerFactory;
    private ActivationRuleFactory activationRuleFactory;
    private Client client;

    @Before
    public void setUp() {
        activationRuleFactory = mock(ActivationRuleFactory.class);
        client = mock(Client.class);
        contextManagerFactory = new ContextManagerFactory(activationRuleFactory, client);
    }

    @Test
    public void testCreateContextManager_ToolsOutputTruncateManager() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig("ToolsOutputTruncateManager", null, null);

        // Act
        ContextManager contextManager = contextManagerFactory.createContextManager(config);

        // Assert
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof ToolsOutputTruncateManager);
    }

    @Test
    public void testCreateContextManager_ToolsOutputTruncateManagerWithParameters() {
        // Arrange
        Map<String, Object> parameters = Map.of("maxLength", 1000);
        ContextManagerConfig config = new ContextManagerConfig("ToolsOutputTruncateManager", parameters, null);

        // Act
        ContextManager contextManager = contextManagerFactory.createContextManager(config);

        // Assert
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof ToolsOutputTruncateManager);
    }

    @Test
    public void testCreateContextManager_SlidingWindowManager() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig("SlidingWindowManager", null, null);

        // Act
        ContextManager contextManager = contextManagerFactory.createContextManager(config);

        // Assert
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof SlidingWindowManager);
    }

    @Test
    public void testCreateContextManager_SummarizationManager() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig("SummarizationManager", null, null);

        // Act
        ContextManager contextManager = contextManagerFactory.createContextManager(config);

        // Assert
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof SummarizationManager);
    }

    @Test
    public void testCreateContextManager_UnknownType() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig("UnknownManager", null, null);

        // Act & Assert
        try {
            contextManagerFactory.createContextManager(config);
            fail("Expected IllegalArgumentException for unknown manager type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported context manager type"));
        }
    }

    @Test
    public void testCreateContextManager_NullConfig() {
        // Act & Assert
        try {
            contextManagerFactory.createContextManager(null);
            fail("Expected IllegalArgumentException for null config");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be null"));
        }
    }

    @Test
    public void testCreateContextManager_NullType() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig(null, null, null);

        // Act & Assert
        try {
            contextManagerFactory.createContextManager(config);
            fail("Expected IllegalArgumentException for null type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be null"));
        }
    }

    @Test
    public void testCreateContextManager_EmptyType() {
        // Arrange
        ContextManagerConfig config = new ContextManagerConfig("", null, null);

        // Act & Assert
        try {
            contextManagerFactory.createContextManager(config);
            fail("Expected IllegalArgumentException for empty type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported context manager type"));
        }
    }

    @Test
    public void testCreateContextManager_WithActivationConfig() {
        // Arrange
        Map<String, Object> managerConfig = Map.of("maxLength", 1000);
        Map<String, Object> activationConfig = Map.of("enabled", true);
        ContextManagerConfig config = new ContextManagerConfig("ToolsOutputTruncateManager", managerConfig, activationConfig);

        // Act
        ContextManager contextManager = contextManagerFactory.createContextManager(config);

        // Assert
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof ToolsOutputTruncateManager);
    }

    @Test
    public void testCreateContextManager_InitializationFailure() {
        // Arrange - Create a config that might cause initialization issues
        Map<String, Object> invalidConfig = Map.of("invalid", "config");
        ContextManagerConfig config = new ContextManagerConfig("SummarizationManager", invalidConfig, null);

        // Act & Assert - Should still create the manager even with invalid config
        ContextManager contextManager = contextManagerFactory.createContextManager(config);
        assertNotNull(contextManager);
        assertTrue(contextManager instanceof SummarizationManager);
    }
}
