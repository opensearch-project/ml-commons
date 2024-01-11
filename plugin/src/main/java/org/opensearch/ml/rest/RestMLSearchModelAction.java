/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.util.List;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;

/**
 * This class consists of the REST handler to search ML Models.
 */
public class RestMLSearchModelAction extends AbstractMLSearchAction<MLModel> {
    private static final String ML_SEARCH_MODEL_ACTION = "ml_search_model_action";
    private static final String SEARCH_MODEL_PATH = ML_BASE_URI + "/models/_search";

    public RestMLSearchModelAction() {
        super(List.of(SEARCH_MODEL_PATH), ML_MODEL_INDEX, MLModel.class, MLModelSearchAction.INSTANCE);
    }

    @Override
    public String getName() {
        return ML_SEARCH_MODEL_ACTION;
    }
}
