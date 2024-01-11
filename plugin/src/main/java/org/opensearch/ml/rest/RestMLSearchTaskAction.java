/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.util.List;

import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;

public class RestMLSearchTaskAction extends AbstractMLSearchAction<MLTask> {
    private static final String ML_SEARCH_Task_ACTION = "ml_search_task_action";
    private static final String SEARCH_TASK_PATH = ML_BASE_URI + "/tasks/_search";

    public RestMLSearchTaskAction() {
        super(List.of(SEARCH_TASK_PATH), ML_TASK_INDEX, MLTask.class, MLTaskSearchAction.INSTANCE);
    }

    @Override
    public String getName() {
        return ML_SEARCH_Task_ACTION;
    }
}
