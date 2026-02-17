/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.skill;

import static org.opensearch.ml.common.CommonValue.ML_SKILLS_INDEX;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLSkill;
import org.opensearch.ml.common.transport.skill.MLGetSkillAction;
import org.opensearch.ml.common.transport.skill.MLGetSkillRequest;
import org.opensearch.ml.common.transport.skill.MLGetSkillResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for getting an agent skill by name.
 * Skill names are used as document IDs in the skills index.
 */
@Log4j2
public class TransportGetSkillAction extends HandledTransportAction<MLGetSkillRequest, MLGetSkillResponse> {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportGetSkillAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLGetSkillAction.NAME, transportService, actionFilters, MLGetSkillRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, MLGetSkillRequest request, ActionListener<MLGetSkillResponse> listener) {
        String skillName = request.getSkillId();  // Note: This is actually the skill name (used as document ID)

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            GetRequest getRequest = new GetRequest(ML_SKILLS_INDEX).id(skillName);

            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    log.error("Skill not found: {}", skillName);
                    listener.onFailure(new OpenSearchStatusException("Skill not found: " + skillName, RestStatus.NOT_FOUND));
                    return;
                }

                try {
                    XContentParser parser = XContentType.JSON
                        .xContent()
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString());
                    parser.nextToken();
                    MLSkill skill = MLSkill.parse(parser);

                    // Note: Skill name is the document ID, already present in the skill object

                    log.info("Successfully retrieved skill: {}", skill.getName());
                    listener.onResponse(new MLGetSkillResponse(skill));
                } catch (Exception e) {
                    log.error("Failed to parse skill document", e);
                    listener
                        .onFailure(
                            new OpenSearchStatusException("Failed to parse skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
                        );
                }
            }, e -> {
                log.error("Failed to get skill: {}", skillName, e);
                listener
                    .onFailure(new OpenSearchStatusException("Failed to get skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to prepare get skill request", e);
            listener.onFailure(new OpenSearchStatusException("Failed to get skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
