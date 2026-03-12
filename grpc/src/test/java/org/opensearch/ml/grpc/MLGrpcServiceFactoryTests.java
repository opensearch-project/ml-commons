/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.java.org.opensearch.ml.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
// import org.opensearch.ml.model.MLModelManager;
// import org.opensearch.ml.task.MLExecuteTaskRunner;
// import org.opensearch.ml.task.MLPredictTaskRunner;

/**
 * Unit tests for MLGrpcServiceFactory.
 */
public class MLGrpcServiceFactoryTests {

    // Use Object types to avoid circular dependency
    private Object mockModelManager;  // MLModelManager
    private Object mockPredictTaskRunner;  // MLPredictTaskRunner
    private Object mockExecuteTaskRunner;  // MLExecuteTaskRunner
    private MLFeatureEnabledSetting mockFeatureSettings;
    private Object mockClient;  // Client
    private Settings mockSettings;

    @Before
    public void setUp() {
        // Create mock objects (type doesn't matter at compile time)
        mockModelManager = new Object();
        mockPredictTaskRunner = new Object();
        mockExecuteTaskRunner = new Object();
        mockFeatureSettings = mock(MLFeatureEnabledSetting.class);
        mockClient = new Object();
        mockSettings = Settings.EMPTY;
    }

    @Test
    public void testFactoryCreation() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );

        assertNotNull("Factory should be created", factory);
    }

    @Test
    public void testInitClient() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );

        factory.initClient(mockClient);

        // Verify no exceptions thrown
    }

    @Test
    public void testInitSettings() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );

        factory.initSettings(mockSettings);

        // Verify no exceptions thrown
    }

    @Test
    public void testBuildWithStreamingEnabled() {
        when(mockFeatureSettings.isStreamEnabled()).thenReturn(true);

        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );
        factory.initClient(mockClient);
        factory.initSettings(mockSettings);

        List<Object> services = factory.build();

        assertNotNull("Services list should not be null", services);
        assertEquals("Should register 2 services", 2, services.size());

        // Verify service types
        boolean hasPredictionService = false;
        boolean hasExecuteService = false;

        for (Object service : services) {
            if (service instanceof MLPredictionStreamService) {
                hasPredictionService = true;
            } else if (service instanceof MLExecuteStreamService) {
                hasExecuteService = true;
            }
        }

        assertTrue("Should register prediction service", hasPredictionService);
        assertTrue("Should register execute service", hasExecuteService);
    }

    @Test
    public void testBuildWithStreamingDisabled() {
        when(mockFeatureSettings.isStreamEnabled()).thenReturn(false);

        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );
        factory.initClient(mockClient);
        factory.initSettings(mockSettings);

        List<Object> services = factory.build();

        assertNotNull("Services list should not be null", services);
        assertEquals("Should not register services when streaming disabled", 0, services.size());
    }

    @Test
    public void testBuildWithoutClient() {
        when(mockFeatureSettings.isStreamEnabled()).thenReturn(true);

        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );
        // Don't call initClient

        List<Object> services = factory.build();

        assertNotNull("Services list should not be null", services);
        assertEquals("Should not register services without client", 0, services.size());
    }

    @Test
    public void testBuildWithNullFeatureSettings() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            null  // null feature settings
        );
        factory.initClient(mockClient);
        factory.initSettings(mockSettings);

        List<Object> services = factory.build();

        assertNotNull("Services list should not be null", services);
        assertEquals("Should not register services with null feature settings", 0, services.size());
    }

    @Test
    public void testBuildWithMissingDependencies() {
        when(mockFeatureSettings.isStreamEnabled()).thenReturn(true);

        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            null,  // null model manager
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );
        factory.initClient(mockClient);
        factory.initSettings(mockSettings);

        List<Object> services = factory.build();

        assertNotNull("Services list should not be null", services);
        assertEquals("Should not register services with missing dependencies", 0, services.size());
    }

    @Test
    public void testGetName() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );

        String name = factory.getName();

        assertNotNull("Name should not be null", name);
        assertEquals("MLGrpcServiceFactory", name);
    }

    @Test
    public void testClose() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );

        // Should not throw exception
        factory.close();
    }

    @Test
    public void testMultipleBuildCalls() {
        when(mockFeatureSettings.isStreamEnabled()).thenReturn(true);

        MLGrpcServiceFactory factory = new MLGrpcServiceFactory(
            mockModelManager,
            mockPredictTaskRunner,
            mockExecuteTaskRunner,
            mockFeatureSettings
        );
        factory.initClient(mockClient);
        factory.initSettings(mockSettings);

        // Call build multiple times
        List<Object> services1 = factory.build();
        List<Object> services2 = factory.build();

        assertNotNull("First services list should not be null", services1);
        assertNotNull("Second services list should not be null", services2);
        assertEquals("Should create same number of services", services1.size(), services2.size());
    }
}
