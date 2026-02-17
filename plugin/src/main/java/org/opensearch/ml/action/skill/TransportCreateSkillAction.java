/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.skill;

import static org.opensearch.ml.common.CommonValue.ML_SKILLS_INDEX;

import java.time.Instant;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLSkill;
import org.opensearch.ml.common.transport.skill.MLCreateSkillAction;
import org.opensearch.ml.common.transport.skill.MLCreateSkillRequest;
import org.opensearch.ml.common.transport.skill.MLCreateSkillResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for creating an agent skill.
 * Skill names are used as document IDs in the skills index.
 */
@Log4j2
public class TransportCreateSkillAction extends HandledTransportAction<MLCreateSkillRequest, MLCreateSkillResponse> {

    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;

    @Inject
    public TransportCreateSkillAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(MLCreateSkillAction.NAME, transportService, actionFilters, MLCreateSkillRequest::new);
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    @Override
    protected void doExecute(Task task, MLCreateSkillRequest request, ActionListener<MLCreateSkillResponse> listener) {
        MLSkill skill = request.getSkill();
        User user = RestActionUtils.getUserContext(client);

        // Check if skills index exists, create if not
        mlIndicesHandler.initMLSkillsIndex(ActionListener.wrap(created -> {
            try {
                // Enrich skill with metadata
                Instant now = Instant.now();
                MLSkill enrichedSkill = skill
                    .toBuilder()
                    .createdTime(now)
                    .lastUpdatedTime(now)
                    .owner(user)
                    .access(skill.getAccess() != null ? skill.getAccess() : AccessMode.PRIVATE)
                    .build();

                // Index the skill document
                indexSkill(enrichedSkill, listener);

            } catch (Exception e) {
                log.error("Failed to create skill", e);
                listener
                    .onFailure(
                        new OpenSearchStatusException("Failed to create skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
                    );
            }
        }, e -> {
            log.error("Failed to initialize skills index", e);
            listener.onFailure(new OpenSearchStatusException("Failed to initialize skills index", RestStatus.INTERNAL_SERVER_ERROR));
        }));
    }

    private void indexSkill(MLSkill skill, ActionListener<MLCreateSkillResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            // Validate skill name format (used as document ID)
            String skillName = skill.getName();
            if (skillName == null || skillName.trim().isEmpty()) {
                listener.onFailure(new OpenSearchStatusException("Skill name cannot be empty", RestStatus.BAD_REQUEST));
                return;
            }
            if (!skillName.matches("^[a-z0-9_-]+$")) {
                listener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Skill name must contain only lowercase letters, numbers, hyphens, and underscores",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }
            if (skillName.length() > 64) {
                listener.onFailure(new OpenSearchStatusException("Skill name cannot exceed 64 characters", RestStatus.BAD_REQUEST));
                return;
            }

            ActionListener<IndexResponse> indexListener = ActionListener.wrap(indexResponse -> {
                if (indexResponse == null || indexResponse.getResult() != DocWriteResponse.Result.CREATED) {
                    log.error("Failed to create skill, result: {}", indexResponse != null ? indexResponse.getResult() : null);
                    listener.onFailure(new OpenSearchStatusException("Failed to create skill", RestStatus.INTERNAL_SERVER_ERROR));
                    return;
                }
                // Document ID is the skill name
                String documentId = indexResponse.getId();
                log.info("Successfully created skill: {}", documentId);
                listener.onResponse(new MLCreateSkillResponse(documentId, "CREATED"));
            }, e -> {
                log.error("Failed to index skill", e);
                listener
                    .onFailure(new OpenSearchStatusException("Failed to index skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
            });

            // Create XContent for the skill
            XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
            skill.toXContent(builder, ToXContent.EMPTY_PARAMS);

            // Use skill name as document ID
            IndexRequest indexRequest = new IndexRequest(ML_SKILLS_INDEX)
                .id(skillName)  // Use skill name as document ID
                .source(builder)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            client.index(indexRequest, ActionListener.runBefore(indexListener, context::restore));

        } catch (Exception e) {
            log.error("Failed to prepare skill for indexing", e);
            listener
                .onFailure(new OpenSearchStatusException("Failed to prepare skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
