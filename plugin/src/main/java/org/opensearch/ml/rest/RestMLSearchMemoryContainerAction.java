/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerSearchAction;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

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

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        return super.prepareRequest(request, client);
    }
}
