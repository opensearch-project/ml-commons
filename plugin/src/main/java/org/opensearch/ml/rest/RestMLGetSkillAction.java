/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.transport.skill.MLGetSkillAction;
import org.opensearch.ml.common.transport.skill.MLGetSkillRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

/**
 * REST handler for getting an agent skill by ID
 */
public class RestMLGetSkillAction extends BaseRestHandler {

    private static final String ML_GET_SKILL_ACTION = "ml_get_skill_action";

    @Override
    public String getName() {
        return ML_GET_SKILL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/skills/{skill_id}", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String skillId = request.param("skill_id");
        MLGetSkillRequest mlGetSkillRequest = MLGetSkillRequest.builder().skillId(skillId).build();
        return channel -> client.execute(MLGetSkillAction.INSTANCE, mlGetSkillRequest, new RestToXContentListener<>(channel));
    }
}
