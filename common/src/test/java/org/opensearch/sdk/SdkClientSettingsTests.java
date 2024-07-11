/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_ENDPOINT;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_ENDPOINT_KEY;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_REGION;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_REGION_KEY;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_TYPE;
import static org.opensearch.sdk.SdkClientSettings.REMOTE_METADATA_TYPE_KEY;

public class SdkClientSettingsTests {

    private Settings settings;
    private ClusterSettings clusterSettings;

    public void setUp() throws Exception {
        settings = Settings.builder().build();
        final Set<Setting<?>> settingsSet = Stream
            .concat(
                ClusterSettings.BUILT_IN_CLUSTER_SETTINGS.stream(),
                Stream
                    .of(
                        REMOTE_METADATA_TYPE,
                        REMOTE_METADATA_ENDPOINT,
                        REMOTE_METADATA_REGION
                    )
            )
            .collect(Collectors.toSet());
        clusterSettings = new ClusterSettings(settings, settingsSet);
    }

    public void testSettings() {
        assertTrue(clusterSettings.isFinalSetting(REMOTE_METADATA_TYPE_KEY));
        assertTrue(clusterSettings.isFinalSetting(REMOTE_METADATA_ENDPOINT_KEY));
        assertTrue(clusterSettings.isFinalSetting(REMOTE_METADATA_REGION_KEY));
    }
}
