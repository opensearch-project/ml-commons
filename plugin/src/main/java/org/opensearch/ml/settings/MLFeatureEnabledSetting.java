/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.settings;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_AGENT_FRAMEWORK_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;

public class MLFeatureEnabledSetting {

    private volatile Boolean isRemoteInferenceEnabled;
    private volatile Boolean isAgentFrameworkEnabled;

    private volatile Boolean isLocalModelEnabled;

    public MLFeatureEnabledSetting(ClusterService clusterService, Settings settings) {
        isRemoteInferenceEnabled = ML_COMMONS_REMOTE_INFERENCE_ENABLED.get(settings);
        isAgentFrameworkEnabled = ML_COMMONS_AGENT_FRAMEWORK_ENABLED.get(settings);
        isLocalModelEnabled = ML_COMMONS_LOCAL_MODEL_ENABLED.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_REMOTE_INFERENCE_ENABLED, it -> isRemoteInferenceEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_AGENT_FRAMEWORK_ENABLED, it -> isAgentFrameworkEnabled = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_LOCAL_MODEL_ENABLED, it -> isLocalModelEnabled = it);
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

}
