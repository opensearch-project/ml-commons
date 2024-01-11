/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.util.List;

import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;

/**
 * This class consists of the REST handler to search ML Models.
 */
public class RestMLSearchModelGroupAction extends AbstractMLSearchAction<MLModelGroup> {
    private static final String ML_SEARCH_MODEL_GROUP_ACTION = "ml_search_model_group_action";
    private static final String SEARCH_MODEL_GROUP_PATH = ML_BASE_URI + "/model_groups/_search";

    public RestMLSearchModelGroupAction() {
        super(List.of(SEARCH_MODEL_GROUP_PATH), ML_MODEL_GROUP_INDEX, MLModelGroup.class, MLModelGroupSearchAction.INSTANCE);
    }

    @Override
    public String getName() {
        return ML_SEARCH_MODEL_GROUP_ACTION;
    }
}
