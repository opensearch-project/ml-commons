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
import org.opensearch.ml.common.transport.skill.MLUpdateSkillAction;
import org.opensearch.ml.common.transport.skill.MLUpdateSkillRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

/**
 * REST action to update an agent skill.
 * PUT /_plugins/_ml/skills/{skill_id}
 */
@Log4j2
public class RestMLUpdateSkillAction extends BaseRestHandler {

    private static final String ML_UPDATE_SKILL_ACTION = "ml_update_skill_action";

    @Override
    public String getName() {
        return ML_UPDATE_SKILL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/skills/{skill_id}", ML_BASE_URI)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String skillId = request.param("skill_id");

        if (skillId == null || skillId.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill ID cannot be null or empty");
        }

        if (!request.hasContent()) {
            throw new IllegalArgumentException("Request body is required");
        }

        // Parse the update content from request body
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLSkill updateContent = MLSkill.parse(parser);

        MLUpdateSkillRequest mlUpdateSkillRequest = new MLUpdateSkillRequest(skillId, updateContent);

        return channel -> client.execute(MLUpdateSkillAction.INSTANCE, mlUpdateSkillRequest, new RestToXContentListener<>(channel));
    }
}
