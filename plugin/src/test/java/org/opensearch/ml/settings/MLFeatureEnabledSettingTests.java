/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENT_FRAMEWORK_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONTROLLER_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_EXECUTE_TOOL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_METRIC_COLLECTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.settings.SettingsChangeListener;

public class MLFeatureEnabledSettingTests {
    @Mock
    private ClusterService clusterService;
    private Settings settings;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private SettingsChangeListener listener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        settings = Settings
            .builder()
            .put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false)
            .put(ML_COMMONS_METRIC_COLLECTION_ENABLED.getKey(), true)
            .put(ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED.getKey(), false)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings())
            .thenReturn(
                new ClusterSettings(
                    settings,
                    Set
                        .of(
                            ML_COMMONS_MULTI_TENANCY_ENABLED,
                            ML_COMMONS_REMOTE_INFERENCE_ENABLED,
                            ML_COMMONS_AGENT_FRAMEWORK_ENABLED,
                            ML_COMMONS_LOCAL_MODEL_ENABLED,
                            ML_COMMONS_CONTROLLER_ENABLED,
                            ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED,
                            ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED,
                            ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED,
                            ML_COMMONS_MCP_SERVER_ENABLED,
                            ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED,
                            ML_COMMONS_METRIC_COLLECTION_ENABLED,
                            ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED,
                            ML_COMMONS_EXECUTE_TOOL_ENABLED,
                            ML_COMMONS_AGENTIC_SEARCH_ENABLED,
                            ML_COMMONS_AGENTIC_MEMORY_ENABLED,
                            ML_COMMONS_MCP_CONNECTOR_ENABLED
                        )
                )
            );

        mlFeatureEnabledSetting = new MLFeatureEnabledSetting(clusterService, settings);
        listener = mock(SettingsChangeListener.class);
    }

    @Test
    public void testAddListenerAndNotify() {
        mlFeatureEnabledSetting.addListener(listener);

        // Simulate settings change
        mlFeatureEnabledSetting.notifyMultiTenancyListeners(false);

        // Verify listener is notified
        verify(listener, times(1)).onMultiTenancyEnabledChanged(false);
    }

    @Test
    public void testMetricCollectionSettings() {
        // Test initial values
        assertTrue(mlFeatureEnabledSetting.isMetricCollectionEnabled());
        assertFalse(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled());

        // Simulate settings change
        Settings newSettings = Settings
            .builder()
            .put(ML_COMMONS_METRIC_COLLECTION_ENABLED.getKey(), false)
            .put(ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED.getKey(), true)
            .build();

        // Update settings through cluster service
        when(clusterService.getSettings()).thenReturn(newSettings);
        mlFeatureEnabledSetting = new MLFeatureEnabledSetting(clusterService, newSettings);

        // Verify updated values
        assertFalse(mlFeatureEnabledSetting.isMetricCollectionEnabled());
        assertTrue(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled());
    }

    @Test
    public void testToolExecuteSettings() {
        // Test initial values
        assertFalse(mlFeatureEnabledSetting.isToolExecuteEnabled());

        // Simulate settings change
        Settings newSettings = Settings.builder().put(ML_COMMONS_EXECUTE_TOOL_ENABLED.getKey(), true).build();

        // Update settings through cluster service
        when(clusterService.getSettings()).thenReturn(newSettings);
        mlFeatureEnabledSetting = new MLFeatureEnabledSetting(clusterService, newSettings);

        // Verify updated values
        assertTrue(mlFeatureEnabledSetting.isToolExecuteEnabled());
    }
}
