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
import org.opensearch.ml.grpc.interfaces.MLModelManager;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;

/**
 * Unit tests for MLGrpcServiceFactory.
 */
public class MLGrpcServiceFactoryTests {

    private MLModelManager mockModelManager;
    private MLFeatureEnabledSetting mockFeatureSettings;
    private MLClient mockClient;
    private MLUserContextProvider mockUserContextProvider;

    @Before
    public void setUp() {
        mockModelManager = mock(MLModelManager.class);
        mockFeatureSettings = mock(MLFeatureEnabledSetting.class);
        mockClient = mock(MLClient.class);
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
        MLGrpcServiceFactory.initialize(null, mockFeatureSettings, mockClient, mockUserContextProvider);
    }
}
