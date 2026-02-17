/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.skill;

import static org.opensearch.ml.common.CommonValue.ML_SKILLS_INDEX;

import java.time.Instant;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLSkill;
import org.opensearch.ml.common.transport.skill.MLUpdateSkillAction;
import org.opensearch.ml.common.transport.skill.MLUpdateSkillRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for updating an agent skill.
 * Skill names are used as document IDs in the skills index.
 */
@Log4j2
public class TransportUpdateSkillAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportUpdateSkillAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLUpdateSkillAction.NAME, transportService, actionFilters, MLUpdateSkillRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        MLUpdateSkillRequest updateRequest = MLUpdateSkillRequest.fromActionRequest(request);
        String skillId = updateRequest.getSkillId();  // This is the skill name (used as document ID)
        MLSkill updateContent = updateRequest.getUpdateContent();
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            // First, get the existing skill to validate access and merge updates
            GetRequest getRequest = new GetRequest(ML_SKILLS_INDEX).id(skillId);

            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    log.error("Skill not found: {}", skillId);
                    listener.onFailure(new OpenSearchStatusException("Skill not found: " + skillId, RestStatus.NOT_FOUND));
                    return;
                }

                try {
                    // Parse existing skill
                    XContentParser parser = XContentType.JSON
                        .xContent()
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString());
                    parser.nextToken();
                    MLSkill existingSkill = MLSkill.parse(parser);

                    // Validate access - only owner can update
                    if (!validateAccess(existingSkill, user)) {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "You don't have permission to update this skill: " + skillId,
                                    RestStatus.FORBIDDEN
                                )
                            );
                        return;
                    }

                    // Merge updates with existing skill
                    MLSkill updatedSkill = mergeSkillUpdates(existingSkill, updateContent);

                    // Update the skill document
                    updateSkill(skillId, updatedSkill, listener);

                } catch (Exception e) {
                    log.error("Failed to parse existing skill document", e);
                    listener
                        .onFailure(
                            new OpenSearchStatusException("Failed to parse skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
                        );
                }
            }, e -> {
                log.error("Failed to get skill: {}", skillId, e);
                listener
                    .onFailure(new OpenSearchStatusException("Failed to get skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to prepare update skill request", e);
            listener
                .onFailure(new OpenSearchStatusException("Failed to update skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private boolean validateAccess(MLSkill existingSkill, User user) {
        // If no owner is set, allow update (backward compatibility)
        if (existingSkill.getOwner() == null) {
            return true;
        }

        // If user is null, deny access
        if (user == null) {
            return false;
        }

        // Check if user is the owner
        return existingSkill.getOwner().getName().equals(user.getName());
    }

    private MLSkill mergeSkillUpdates(MLSkill existing, MLSkill updates) {
        // Build updated skill, preserving fields that shouldn't be changed
        return existing
            .toBuilder()
            // Update allowed fields
            .description(updates.getDescription() != null ? updates.getDescription() : existing.getDescription())
            .license(updates.getLicense() != null ? updates.getLicense() : existing.getLicense())
            .compatibility(updates.getCompatibility() != null ? updates.getCompatibility() : existing.getCompatibility())
            .metadata(updates.getMetadata() != null ? updates.getMetadata() : existing.getMetadata())
            .allowedTools(updates.getAllowedTools() != null ? updates.getAllowedTools() : existing.getAllowedTools())
            .instructions(updates.getInstructions() != null ? updates.getInstructions() : existing.getInstructions())
            .scripts(updates.getScripts() != null ? updates.getScripts() : existing.getScripts())
            .references(updates.getReferences() != null ? updates.getReferences() : existing.getReferences())
            .assets(updates.getAssets() != null ? updates.getAssets() : existing.getAssets())
            // Preserve immutable fields
            .name(existing.getName())  // Name cannot be changed (it's the document ID)
            .tenantId(existing.getTenantId())
            .backendRoles(existing.getBackendRoles())
            .owner(existing.getOwner())
            .access(existing.getAccess())
            .createdTime(existing.getCreatedTime())
            // Update timestamp
            .lastUpdatedTime(Instant.now())
            .build();
    }

    private void updateSkill(String skillId, MLSkill skill, ActionListener<UpdateResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            // Create XContent for the updated skill
            XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
            skill.toXContent(builder, ToXContent.EMPTY_PARAMS);

            UpdateRequest updateRequest = new UpdateRequest(ML_SKILLS_INDEX, skillId)
                .doc(builder)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            ActionListener<UpdateResponse> updateListener = ActionListener.wrap(updateResponse -> {
                if (updateResponse == null || updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                    log
                        .error(
                            "Failed to update skill: {}, result: {}",
                            skillId,
                            updateResponse != null ? updateResponse.getResult() : null
                        );
                    listener.onFailure(new OpenSearchStatusException("Failed to update skill", RestStatus.INTERNAL_SERVER_ERROR));
                    return;
                }
                log.info("Successfully updated skill: {}", skillId);
                listener.onResponse(updateResponse);
            }, e -> {
                log.error("Failed to update skill: {}", skillId, e);
                listener
                    .onFailure(
                        new OpenSearchStatusException("Failed to update skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
                    );
            });

            client.update(updateRequest, ActionListener.runBefore(updateListener, context::restore));

        } catch (Exception e) {
            log.error("Failed to prepare skill for update", e);
            listener
                .onFailure(new OpenSearchStatusException("Failed to prepare skill: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
