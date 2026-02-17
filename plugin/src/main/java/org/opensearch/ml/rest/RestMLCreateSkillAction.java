/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLSkill;
import org.opensearch.ml.common.transport.skill.MLCreateSkillAction;
import org.opensearch.ml.common.transport.skill.MLCreateSkillRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * REST handler for creating agent skills
 */
public class RestMLCreateSkillAction extends BaseRestHandler {

    private static final String ML_CREATE_SKILL_ACTION = "ml_create_skill_action";

    @Override
    public String getName() {
        return ML_CREATE_SKILL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/skills/_create", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateSkillRequest mlCreateSkillRequest = getRequest(request);
        return channel -> client.execute(MLCreateSkillAction.INSTANCE, mlCreateSkillRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCreateSkillRequest from REST request
     */
    @VisibleForTesting
    MLCreateSkillRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Request body is required");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLSkill skill = MLSkill.parse(parser);
        return MLCreateSkillRequest.builder().skill(skill).build();
    }
}
