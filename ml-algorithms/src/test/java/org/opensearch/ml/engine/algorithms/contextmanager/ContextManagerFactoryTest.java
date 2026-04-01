/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.transport.client.Client;

public class ContextManagerFactoryTest {

    private Client mockClient;
    private ContextManagementTemplate mockTemplate;

    @Before
    public void setUp() {
        mockClient = mock(Client.class);
        mockTemplate = mock(ContextManagementTemplate.class);
    }

    @Test
    public void testCreateToolsOutputTruncateManager() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxLength", 1000);

        ContextManagerConfig managerConfig = new ContextManagerConfig(ToolsOutputTruncateManager.TYPE, new HashMap<>(), config);

        ContextManager manager = ContextManagerFactory.createContextManager(managerConfig, mockClient);

        assertNotNull(manager);
        assertTrue(manager instanceof ToolsOutputTruncateManager);
    }

    @Test
    public void testCreateSummarizationManager() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxTokens", 500);

        ContextManagerConfig managerConfig = new ContextManagerConfig(SummarizationManager.TYPE, new HashMap<>(), config);

        ContextManager manager = ContextManagerFactory.createContextManager(managerConfig, mockClient);

        assertNotNull(manager);
        assertTrue(manager instanceof SummarizationManager);
    }

    @Test
    public void testCreateMemoryManager() {
        Map<String, Object> config = new HashMap<>();
        config.put("windowSize", 10);

        ContextManagerConfig managerConfig = new ContextManagerConfig(SlidingWindowManager.TYPE, new HashMap<>(), config);

        ContextManager manager = ContextManagerFactory.createContextManager(managerConfig, mockClient);

        assertNotNull(manager);
        assertTrue(manager instanceof SlidingWindowManager);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateUnknownContextManager() {
        ContextManagerConfig managerConfig = new ContextManagerConfig("UnknownManager", new HashMap<>(), new HashMap<>());

        ContextManagerFactory.createContextManager(managerConfig, mockClient);
    }

    @Test
    public void testCreateContextManagersFromTemplate() {
        // Create a mock template with context managers
        Map<String, List<ContextManagerConfig>> hooks = new HashMap<>();

        List<ContextManagerConfig> configs = List
            .of(
                new ContextManagerConfig(ToolsOutputTruncateManager.TYPE, new HashMap<>(), new HashMap<>()),
                new ContextManagerConfig(SummarizationManager.TYPE, new HashMap<>(), new HashMap<>())
            );

        hooks.put("POST_TOOL", configs);

        ContextManagementTemplate template = ContextManagementTemplate.builder().name("test-template").hooks(hooks).build();

        List<ContextManager> managers = ContextManagerFactory.createContextManagers(template, mockClient);

        assertNotNull(managers);
        assertEquals(2, managers.size());
        assertTrue(managers.get(0) instanceof ToolsOutputTruncateManager);
        assertTrue(managers.get(1) instanceof SummarizationManager);
    }
}
