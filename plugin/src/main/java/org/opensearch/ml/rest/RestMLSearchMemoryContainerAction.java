/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerSearchAction;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to search ML Models.
 */
public class RestMLSearchMemoryContainerAction extends AbstractMLSearchAction<MLMemoryContainer> {
    private static final String ML_SEARCH_MODEL_GROUP_ACTION = "ml_search_memory_container_action";
    private static final String SEARCH_MEMORY_CONTAINER_PATH = ML_BASE_URI + "/memory_containers/_search";

    public RestMLSearchMemoryContainerAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(
            ImmutableList.of(SEARCH_MEMORY_CONTAINER_PATH),
            ML_MEMORY_CONTAINER_INDEX,
            MLMemoryContainer.class,
            MLMemoryContainerSearchAction.INSTANCE,
            mlFeatureEnabledSetting
        );
    }

    @Override
    public String getName() {
        return ML_SEARCH_MODEL_GROUP_ACTION;
    }
}
