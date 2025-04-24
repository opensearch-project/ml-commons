/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetTaskAction extends BaseRestHandler {
    private static final String ML_GET_TASK_ACTION = "ml_get_task_action";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetTaskAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_TASK_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/tasks/{%s}", ML_BASE_URI, PARAMETER_TASK_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        MLTaskGetRequest mlTaskGetRequest = getRequest(request, tenantId);
        return channel -> client.execute(MLTaskGetAction.INSTANCE, mlTaskGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTaskGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTaskGetRequest
     */
    @VisibleForTesting
    MLTaskGetRequest getRequest(RestRequest request, String tenantId) throws IOException {
        String taskId = getParameterId(request, PARAMETER_TASK_ID);

        return new MLTaskGetRequest(taskId, tenantId);
    }
}
