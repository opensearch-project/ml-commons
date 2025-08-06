/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;

import com.google.common.annotations.VisibleForTesting;

public class MLFeatureEnabledSetting {

    private volatile Boolean isRemoteInferenceEnabled;
    private volatile Boolean isAgentFrameworkEnabled;

    private volatile Boolean isLocalModelEnabled;
    private volatile AtomicBoolean isConnectorPrivateIpEnabled;

    private volatile Boolean isControllerEnabled;
    private volatile Boolean isBatchIngestionEnabled;
    private volatile Boolean isBatchInferenceEnabled;

    // This is to identify if this node is in multi-tenancy or not.
    private volatile Boolean isMultiTenancyEnabled;

    private volatile Boolean isMcpServerEnabled;

    private volatile Boolean isRagSearchPipelineEnabled;

    private volatile Boolean isMetricCollectionEnabled;
    private volatile Boolean isStaticMetricCollectionEnabled;

    private volatile Boolean isExecuteToolEnabled;

    private volatile Boolean isAgenticSearchEnabled;

    private volatile Boolean isMcpConnectorEnabled;

    private volatile Boolean isAgenticMemoryEnabled;

    private final List<SettingsChangeListener> listeners = new ArrayList<>();

    public MLFeatureEnabledSetting(ClusterService clusterService, Settings settings) {
        isRemoteInferenceEnabled = ML_COMMONS_REMOTE_INFERENCE_ENABLED.get(settings);
        isAgentFrameworkEnabled = ML_COMMONS_AGENT_FRAMEWORK_ENABLED.get(settings);
        isLocalModelEnabled = ML_COMMONS_LOCAL_MODEL_ENABLED.get(settings);
        isConnectorPrivateIpEnabled = new AtomicBoolean(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED.get(settings));
        isControllerEnabled = ML_COMMONS_CONTROLLER_ENABLED.get(settings);
        isBatchIngestionEnabled = ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED.get(settings);
        isBatchInferenceEnabled = ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED.get(settings);
        isMultiTenancyEnabled = ML_COMMONS_MULTI_TENANCY_ENABLED.get(settings);
        isMcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(settings);
        isRagSearchPipelineEnabled = ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED.get(settings);
        isMetricCollectionEnabled = ML_COMMONS_METRIC_COLLECTION_ENABLED.get(settings);
        isStaticMetricCollectionEnabled = ML_COMMONS_STATIC_METRIC_COLLECTION_ENABLED.get(settings);
        isExecuteToolEnabled = ML_COMMONS_EXECUTE_TOOL_ENABLED.get(settings);
        isAgenticSearchEnabled = ML_COMMONS_AGENTIC_SEARCH_ENABLED.get(settings);
        isMcpConnectorEnabled = ML_COMMONS_MCP_CONNECTOR_ENABLED.get(settings);
        isAgenticMemoryEnabled = ML_COMMONS_AGENTIC_MEMORY_ENABLED.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_REMOTE_INFERENCE_ENABLED, it -> isRemoteInferenceEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_AGENT_FRAMEWORK_ENABLED, it -> isAgentFrameworkEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_LOCAL_MODEL_ENABLED, it -> isLocalModelEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED, it -> isConnectorPrivateIpEnabled.set(it));
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_CONTROLLER_ENABLED, it -> isControllerEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED, it -> isBatchIngestionEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED, it -> isBatchInferenceEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> isMcpServerEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED, it -> isRagSearchPipelineEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_EXECUTE_TOOL_ENABLED, it -> isExecuteToolEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_AGENTIC_SEARCH_ENABLED, it -> isAgenticSearchEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_CONNECTOR_ENABLED, it -> isMcpConnectorEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_AGENTIC_MEMORY_ENABLED, it -> isAgenticMemoryEnabled = it);
    }

    /**
     * Whether the remote inference feature is enabled. If disabled, APIs in ml-commons will block remote inference.
     * @return whether Remote Inference is enabled.
     */
    public boolean isRemoteInferenceEnabled() {
        return isRemoteInferenceEnabled;
    }

    /**
     * Whether the agent framework feature is enabled. If disabled, APIs in ml-commons will block agent framework.
     * @return whether the agent framework is enabled.
     */
    public boolean isAgentFrameworkEnabled() {
        return isAgentFrameworkEnabled;
    }

    /**
     * Whether the local model feature is enabled. If disabled, APIs in ml-commons will block local model inference.
     * @return whether the local inference is enabled.
     */
    public boolean isLocalModelEnabled() {
        return isLocalModelEnabled;
    }

    public AtomicBoolean isConnectorPrivateIpEnabled() {
        return isConnectorPrivateIpEnabled;
    }

    /**
     * Whether the controller feature is enabled. If disabled, APIs in ml-commons will block controller.
     * @return whether the controller is enabled.
     */
    public Boolean isControllerEnabled() {
        return isControllerEnabled;
    }

    /**
     * Whether the offline batch ingestion is enabled. If disabled, APIs in ml-commons will block offline batch ingestion.
     * @return whether the feature is enabled.
     */
    public Boolean isOfflineBatchIngestionEnabled() {
        return isBatchIngestionEnabled;
    }

    /**
     * Whether the offline batch inference is enabled. If disabled, APIs in ml-commons will block offline batch inference.
     * @return whether the feature is enabled.
     */
    public Boolean isOfflineBatchInferenceEnabled() {
        return isBatchInferenceEnabled;
    }

    /**
     * Whether the multi-tenancy feature is enabled. If disabled, tenant id will be null.
     * @return whether the multi tenancy feature is enabled.
     */
    public boolean isMultiTenancyEnabled() {
        return isMultiTenancyEnabled;
    }

    /**
     * Whether the mcp server feature is enabled. If disabled, MCP server APIs in ml-commons will be blocked.
     * @return where the MCP server feature is enabled.
     */
    public boolean isMcpServerEnabled() {
        return isMcpServerEnabled;
    }

    public void addListener(SettingsChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Whether the rag search pipeline feature is enabled. If disabled, APIs in ml-commons will block rag search pipeline.
     * @return whether the feature is enabled.
     */
    public boolean isRagSearchPipelineEnabled() {
        return isRagSearchPipelineEnabled;
    }

    public boolean isMetricCollectionEnabled() {
        return isMetricCollectionEnabled;
    }

    public boolean isStaticMetricCollectionEnabled() {
        return isStaticMetricCollectionEnabled;
    }

    /**
     * Whether the execute tool API is enabled. If disabled, execute tool API in ml-commons will be blocked
     * @return whether the execute tool API is enabled.
     */
    public boolean isToolExecuteEnabled() {
        return isExecuteToolEnabled;
    }

    /**
     * Whether the Agentic memory APIs are enabled. If disabled, Agentic memory APIs in ml-commons will be blocked
     * @return whether the agentic memory feature is enabled.
     */
    public boolean isAgenticMemoryEnabled() {
        return isAgenticMemoryEnabled;
    }

    @VisibleForTesting
    public void notifyMultiTenancyListeners(boolean isEnabled) {
        for (SettingsChangeListener listener : listeners) {
            listener.onMultiTenancyEnabledChanged(isEnabled);
        }
    }

    public boolean isAgenticSearchEnabled() {
        return isAgenticSearchEnabled;
    }

    public boolean isMcpConnectorEnabled() {
        return isMcpConnectorEnabled;
    }
}
