/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.settings;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_AGENT_FRAMEWORK_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONTROLLER_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;

public class MLFeatureEnabledSetting {

    private volatile Boolean isRemoteInferenceEnabled;
    private volatile Boolean isAgentFrameworkEnabled;

    private volatile Boolean isLocalModelEnabled;
    private volatile AtomicBoolean isConnectorPrivateIpEnabled;

    private volatile Boolean isControllerEnabled;
    private volatile Boolean isBatchIngestionEnabled;
    private volatile Boolean isBatchInferenceEnabled;

    public MLFeatureEnabledSetting(ClusterService clusterService, Settings settings) {
        isRemoteInferenceEnabled = ML_COMMONS_REMOTE_INFERENCE_ENABLED.get(settings);
        isAgentFrameworkEnabled = ML_COMMONS_AGENT_FRAMEWORK_ENABLED.get(settings);
        isLocalModelEnabled = ML_COMMONS_LOCAL_MODEL_ENABLED.get(settings);
        isConnectorPrivateIpEnabled = new AtomicBoolean(ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED.get(settings));
        isControllerEnabled = ML_COMMONS_CONTROLLER_ENABLED.get(settings);
        isBatchIngestionEnabled = ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED.get(settings);
        isBatchInferenceEnabled = ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED.get(settings);

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
}
