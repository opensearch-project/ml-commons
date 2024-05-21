package org.opensearch.ml.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_BACKOFF_MILLIS;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_RETRY_TIMEOUT_SECONDS;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.engine.algorithms.remote.ConnectorRetryOption;
import org.opensearch.test.OpenSearchTestCase;

public class RemoteInferenceRetryUtilsTests extends OpenSearchTestCase {
    public static boolean TEST_RETRY_ENABLED = false;
    public static Integer TEST_RETRY_BACKOFF_MILLIS = 123;
    public static Integer TEST_RETRY_TIMEOUT_SECONDS = 45;

    private Settings settings;
    private ClusterSettings clusterSettings;
    private ClusterService clusterService;

    @Before
    public void setUpRemoteInferenceRetryUtils() {
        settings = Settings.builder().build();
        final Set<Setting<?>> settingsSet = Stream
            .concat(
                ClusterSettings.BUILT_IN_CLUSTER_SETTINGS.stream(),
                Stream
                    .of(
                        ML_COMMONS_REMOTE_INFERENCE_RETRY_ENABLED,
                        ML_COMMONS_REMOTE_INFERENCE_RETRY_BACKOFF_MILLIS,
                        ML_COMMONS_REMOTE_INFERENCE_RETRY_TIMEOUT_SECONDS
                    )
            )
            .collect(Collectors.toSet());
        clusterSettings = new ClusterSettings(settings, settingsSet);
        clusterService = mock(ClusterService.class);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        RemoteInferenceRetryUtils.initialize(clusterService, settings);
    }

    @Test
    public void testDefaultValue() {
        ConnectorRetryOption connectorRetryOption = RemoteInferenceRetryUtils.getRetryOptionFromClusterSettings();
        assertEquals(true, connectorRetryOption.isRetryEnabled());
        assertEquals(Optional.of(100), Optional.of(connectorRetryOption.getRetryBackoffMillis()));
        assertEquals(Optional.of(30), Optional.of(connectorRetryOption.getRetryTimeoutSeconds()));
        assertEquals("opensearch_ml_predict_remote", connectorRetryOption.getRetyExecutor());
    }

    @Test
    public void testUpdateClusterSettings_thenReturnUpdatedOption() {
        Settings newSettings = Settings
            .builder()
            .put("plugins.ml_commons.remote_inference.retry_enabled", TEST_RETRY_ENABLED)
            .put("plugins.ml_commons.remote_inference.retry_backoff_millis", TEST_RETRY_BACKOFF_MILLIS)
            .put("plugins.ml_commons.remote_inference.retry_timeout_seconds", TEST_RETRY_TIMEOUT_SECONDS)
            .build();

        clusterSettings.applySettings(newSettings);
        ConnectorRetryOption connectorRetryOption = RemoteInferenceRetryUtils.getRetryOptionFromClusterSettings();
        assertEquals(TEST_RETRY_ENABLED, connectorRetryOption.isRetryEnabled());
        assertEquals(Optional.of(TEST_RETRY_BACKOFF_MILLIS), Optional.of(connectorRetryOption.getRetryBackoffMillis()));
        assertEquals(Optional.of(TEST_RETRY_TIMEOUT_SECONDS), Optional.of(connectorRetryOption.getRetryTimeoutSeconds()));
        assertEquals("opensearch_ml_predict_remote", connectorRetryOption.getRetyExecutor());
    }
}
