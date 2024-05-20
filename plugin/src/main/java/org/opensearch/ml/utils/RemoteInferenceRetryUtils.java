package org.opensearch.ml.utils;

import static org.opensearch.ml.plugin.MachineLearningPlugin.REMOTE_PREDICT_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_BACKOFF_MILLIS;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_TIMEOUT_SECONDS;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.engine.algorithms.remote.ConnectorRetryOption;

public class RemoteInferenceRetryUtils {
    private static volatile boolean retryEnabled;
    private static volatile Integer retryBackoffMillis;
    private static volatile Integer retryTimeoutSeconds;

    public static void initialize(ClusterService clusterService, Settings settings) {
        retryEnabled = ML_COMMONS_REMOTE_INFERENCE_RETRY_ENABLED.get(settings);
        retryBackoffMillis = ML_COMMONS_REMOTE_INFERENCE_RETRY_BACKOFF_MILLIS.get(settings);
        retryTimeoutSeconds = ML_COMMONS_REMOTE_INFERENCE_RETRY_TIMEOUT_SECONDS.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_REMOTE_INFERENCE_RETRY_ENABLED, it -> retryEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_REMOTE_INFERENCE_RETRY_BACKOFF_MILLIS, it -> retryBackoffMillis = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_REMOTE_INFERENCE_RETRY_TIMEOUT_SECONDS, it -> retryTimeoutSeconds = it);
    }

    public static ConnectorRetryOption getRetryOptionFromClusterSettings() {
        ConnectorRetryOption connectorRetryOption = new ConnectorRetryOption();
        connectorRetryOption.setRetryEnabled(retryEnabled);
        connectorRetryOption.setRetryBackoffMillis(retryBackoffMillis);
        connectorRetryOption.setRetryTimeoutSeconds(retryTimeoutSeconds);
        connectorRetryOption.setRetyExecutor(REMOTE_PREDICT_THREAD_POOL);
        return connectorRetryOption;
    }
}
