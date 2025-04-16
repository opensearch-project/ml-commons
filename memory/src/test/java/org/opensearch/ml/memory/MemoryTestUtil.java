/*
 * Copyright 2024 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.settings.MLCommonsSettings;

public class MemoryTestUtil {

    public static ClusterService clusterServiceWithMemoryFeatureDisabled() {
        ClusterService mockClusterService = mock(ClusterService.class);
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), false).build();
        when(mockClusterService.getSettings()).thenReturn(settings);
        when(mockClusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED)));
        return mockClusterService;
    }

}
