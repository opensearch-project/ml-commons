package org.opensearch.ml.common.contextmanager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ContextManagerHookProviderEnhancedTest {

    @Mock
    private ContextManager mockManager1;

    @Mock
    private ContextManager mockManager2;

    private List<ContextManager> contextManagers;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockManager1.getType()).thenReturn("TestManager1");
        when(mockManager2.getType()).thenReturn("TestManager2");

        contextManagers = new ArrayList<>();
        contextManagers.add(mockManager1);
        contextManagers.add(mockManager2);
    }

    @Test
    public void testConstructorWithoutConfiguration() {
        ContextManagerHookProvider provider = new ContextManagerHookProvider(contextManagers);

        // Should have no managers organized initially
        assertEquals(0, provider.getManagerCount("PRE_LLM"));
        assertEquals(0, provider.getManagerCount("POST_TOOL"));
    }

    @Test
    public void testConstructorWithConfiguration() {
        Map<String, List<ContextManagerConfig>> hookConfiguration = createTestConfiguration();

        ContextManagerHookProvider provider = new ContextManagerHookProvider(contextManagers, hookConfiguration);

        // Should have managers organized according to configuration
        assertEquals(1, provider.getManagerCount("PRE_LLM"));
        assertEquals(1, provider.getManagerCount("POST_TOOL"));
    }

    @Test
    public void testUpdateHookConfiguration() {
        ContextManagerHookProvider provider = new ContextManagerHookProvider(contextManagers);

        // Initially no managers organized
        assertEquals(0, provider.getManagerCount("PRE_LLM"));

        // Update with configuration
        Map<String, List<ContextManagerConfig>> hookConfiguration = createTestConfiguration();
        provider.updateHookConfiguration(hookConfiguration);

        // Should now have managers organized
        assertEquals(1, provider.getManagerCount("PRE_LLM"));
        assertEquals(1, provider.getManagerCount("POST_TOOL"));
    }

    @Test
    public void testConfigurationBasedOrganization() {
        Map<String, List<ContextManagerConfig>> hookConfiguration = new HashMap<>();

        // Configure TestManager1 for PRE_LLM hook
        List<ContextManagerConfig> preLLMConfigs = new ArrayList<>();
        ContextManagerConfig config1 = mock(ContextManagerConfig.class);
        when(config1.getType()).thenReturn("TestManager1");
        preLLMConfigs.add(config1);
        hookConfiguration.put("PRE_LLM", preLLMConfigs);

        // Configure TestManager2 for POST_TOOL hook
        List<ContextManagerConfig> postToolConfigs = new ArrayList<>();
        ContextManagerConfig config2 = mock(ContextManagerConfig.class);
        when(config2.getType()).thenReturn("TestManager2");
        postToolConfigs.add(config2);
        hookConfiguration.put("POST_TOOL", postToolConfigs);

        ContextManagerHookProvider provider = new ContextManagerHookProvider(contextManagers, hookConfiguration);

        // Verify correct organization
        assertEquals(1, provider.getManagerCount("PRE_LLM"));
        assertEquals(1, provider.getManagerCount("POST_TOOL"));
        assertEquals(0, provider.getManagerCount("POST_MEMORY"));
    }

    private Map<String, List<ContextManagerConfig>> createTestConfiguration() {
        Map<String, List<ContextManagerConfig>> hookConfiguration = new HashMap<>();

        // Configure TestManager1 for PRE_LLM
        List<ContextManagerConfig> preLLMConfigs = new ArrayList<>();
        ContextManagerConfig config1 = mock(ContextManagerConfig.class);
        when(config1.getType()).thenReturn("TestManager1");
        preLLMConfigs.add(config1);
        hookConfiguration.put("PRE_LLM", preLLMConfigs);

        // Configure TestManager2 for POST_TOOL
        List<ContextManagerConfig> postToolConfigs = new ArrayList<>();
        ContextManagerConfig config2 = mock(ContextManagerConfig.class);
        when(config2.getType()).thenReturn("TestManager2");
        postToolConfigs.add(config2);
        hookConfiguration.put("POST_TOOL", postToolConfigs);

        return hookConfiguration;
    }

    @Test
    public void testBackwardCompatibilityWithUpdateHookConfiguration() {
        // Test the old pattern: constructor + updateHookConfiguration
        ContextManagerHookProvider provider = new ContextManagerHookProvider(contextManagers);

        // Initially no managers organized
        assertEquals(0, provider.getManagerCount("PRE_LLM"));

        // Update with configuration (old pattern)
        Map<String, List<ContextManagerConfig>> hookConfiguration = createTestConfiguration();
        provider.updateHookConfiguration(hookConfiguration);

        // Should work the same as new constructor
        assertEquals(1, provider.getManagerCount("PRE_LLM"));
        assertEquals(1, provider.getManagerCount("POST_TOOL"));
    }
}
