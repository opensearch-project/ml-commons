/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.ml.grpc.interfaces.MLModelAccessControlHelper;
import org.opensearch.ml.grpc.interfaces.MLModelManager;
import org.opensearch.ml.grpc.interfaces.MLTaskRunner;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;

/**
 * Unit tests for MLGrpcServiceFactory.
 */
public class MLGrpcServiceFactoryTests {

    private MLModelManager mockModelManager;
    private MLTaskRunner mockPredictTaskRunner;
    private MLTaskRunner mockExecuteTaskRunner;
    private MLFeatureEnabledSetting mockFeatureSettings;
    private MLModelAccessControlHelper mockAccessControlHelper;
    private MLClient mockClient;
    private Object mockSdkClient;
    private MLUserContextProvider mockUserContextProvider;

    @Before
    public void setUp() {
        mockModelManager = mock(MLModelManager.class);
        mockPredictTaskRunner = mock(MLTaskRunner.class);
        mockExecuteTaskRunner = mock(MLTaskRunner.class);
        mockFeatureSettings = mock(MLFeatureEnabledSetting.class);
        mockAccessControlHelper = mock(MLModelAccessControlHelper.class);
        mockClient = mock(MLClient.class);
        mockSdkClient = new Object();
        mockUserContextProvider = mock(MLUserContextProvider.class);
    }

    @After
    public void tearDown() {}

    @Test
    public void testFactoryCreation() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory();
        assertNotNull("Factory should be created", factory);
    }

    @Test
    public void testPlugin() {
        MLGrpcServiceFactory factory = new MLGrpcServiceFactory();
        assertEquals("opensearch-ml", factory.plugin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitializeWithNullModelManager() {
        MLGrpcServiceFactory
            .initialize(
                null,
                mockPredictTaskRunner,
                mockExecuteTaskRunner,
                mockFeatureSettings,
                mockAccessControlHelper,
                mockClient,
                mockSdkClient,
                mockUserContextProvider
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitializeWithNullPredictTaskRunner() {
        MLGrpcServiceFactory
            .initialize(
                mockModelManager,
                null,
                mockExecuteTaskRunner,
                mockFeatureSettings,
                mockAccessControlHelper,
                mockClient,
                mockSdkClient,
                mockUserContextProvider
            );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitializeWithNullExecuteTaskRunner() {
        MLGrpcServiceFactory
            .initialize(
                mockModelManager,
                mockPredictTaskRunner,
                null,
                mockFeatureSettings,
                mockAccessControlHelper,
                mockClient,
                mockSdkClient,
                mockUserContextProvider
            );
    }
}
