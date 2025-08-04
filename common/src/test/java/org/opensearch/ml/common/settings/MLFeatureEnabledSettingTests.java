/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;

public class MLFeatureEnabledSettingTests {

    private ClusterService mockClusterService;
    private ClusterSettings mockClusterSettings;

    @Before
    public void setUp() {
        mockClusterService = mock(ClusterService.class);
        // Create a real ClusterSettings instance with an empty set of registered settings
        mockClusterSettings = new ClusterSettings(
            Settings.EMPTY,
            Set
                .of(
                    MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED,
                    MLCommonsSettings.ML_COMMONS_AGENT_FRAMEWORK_ENABLED,
                    MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ENABLED,
                    MLCommonsSettings.ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED,
                    MLCommonsSettings.ML_COMMONS_CONTROLLER_ENABLED,
                    MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED,
                    MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED,
                    MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED,
                    MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED,
                    MLCommonsSettings.ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED,
                    MLCommonsSettings.ML_COMMONS_METRIC_COLLECTION_ENABLED,
                    MLCommonsSettings.ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED,
                    MLCommonsSettings.ML_COMMONS_EXECUTE_TOOL_ENABLED
                )
        );
        when(mockClusterService.getClusterSettings()).thenReturn(mockClusterSettings);
    }

    @Test
    public void testDefaults_allFeaturesEnabled() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.remote_inference.enabled", true)
            .put("plugins.ml_commons.agent_framework_enabled", true)
            .put("plugins.ml_commons.local_model.enabled", true)
            .put("plugins.ml_commons.connector.private_ip_enabled", true)
            .put("plugins.ml_commons.controller_enabled", true)
            .put("plugins.ml_commons.offline_batch_ingestion_enabled", true)
            .put("plugins.ml_commons.offline_batch_inference_enabled", true)
            .put("plugins.ml_commons.multi_tenancy_enabled", true)
            .put("plugins.ml_commons.mcp_server_enabled", true)
            .put("plugins.ml_commons.rag_pipeline_feature_enabled", true)
            .put("plugins.ml_commons.metrics_collection_enabled", true)
            .put("plugins.ml_commons.metrics_static_collection_enabled", true)
            .build();

        MLFeatureEnabledSetting setting = new MLFeatureEnabledSetting(mockClusterService, settings);

        assertTrue(setting.isRemoteInferenceEnabled());
        assertTrue(setting.isAgentFrameworkEnabled());
        assertTrue(setting.isLocalModelEnabled());
        assertTrue(setting.isConnectorPrivateIpEnabled().get());
        assertTrue(setting.isControllerEnabled());
        assertTrue(setting.isOfflineBatchIngestionEnabled());
        assertTrue(setting.isOfflineBatchInferenceEnabled());
        assertTrue(setting.isMultiTenancyEnabled());
        assertTrue(setting.isMcpServerEnabled());
        assertTrue(setting.isRagSearchPipelineEnabled());
        assertTrue(setting.isMetricCollectionEnabled());
        assertTrue(setting.isStaticMetricCollectionEnabled());
    }

    @Test
    public void testDefaults_someFeaturesDisabled() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.remote_inference.enabled", false)
            .put("plugins.ml_commons.agent_framework_enabled", false)
            .put("plugins.ml_commons.local_model.enabled", false)
            .put("plugins.ml_commons.connector.private_ip_enabled", false)
            .put("plugins.ml_commons.controller_enabled", false)
            .put("plugins.ml_commons.offline_batch_ingestion_enabled", false)
            .put("plugins.ml_commons.offline_batch_inference_enabled", false)
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.mcp_server_enabled", false)
            .put("plugins.ml_commons.rag_pipeline_feature_enabled", false)
            .put("plugins.ml_commons.metrics_collection_enabled", false)
            .put("plugins.ml_commons.metrics_static_collection_enabled", false)
            .build();

        MLFeatureEnabledSetting setting = new MLFeatureEnabledSetting(mockClusterService, settings);

        assertFalse(setting.isRemoteInferenceEnabled());
        assertFalse(setting.isAgentFrameworkEnabled());
        assertFalse(setting.isLocalModelEnabled());
        assertFalse(setting.isConnectorPrivateIpEnabled().get());
        assertFalse(setting.isControllerEnabled());
        assertFalse(setting.isOfflineBatchIngestionEnabled());
        assertFalse(setting.isOfflineBatchInferenceEnabled());
        assertFalse(setting.isMultiTenancyEnabled());
        assertFalse(setting.isMcpServerEnabled());
        assertFalse(setting.isRagSearchPipelineEnabled());
        assertFalse(setting.isMetricCollectionEnabled());
        assertFalse(setting.isStaticMetricCollectionEnabled());
    }

    @Test
    public void testMultiTenancyChangeNotifiesListeners() {
        Settings settings = Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", false).build();

        MLFeatureEnabledSetting setting = new MLFeatureEnabledSetting(mockClusterService, settings);

        SettingsChangeListener mockListener = mock(SettingsChangeListener.class);
        setting.addListener(mockListener);

        setting.notifyMultiTenancyListeners(true);
        verify(mockListener).onMultiTenancyEnabledChanged(true);
    }
}
