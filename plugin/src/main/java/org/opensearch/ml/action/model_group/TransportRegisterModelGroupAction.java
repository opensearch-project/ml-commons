/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

@Log4j2
public class TransportRegisterModelGroupAction extends HandledTransportAction<ActionRequest, MLRegisterModelGroupResponse> {

    private final TransportService transportService;
    private final ActionFilters actionFilters;
    private final MLIndicesHandler mlIndicesHandler;
    private final ThreadPool threadPool;
    private final Client client;

    @Inject
    public TransportRegisterModelGroupAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        ThreadPool threadPool,
        Client client
    ) {
        super(MLRegisterModelGroupAction.NAME, transportService, actionFilters, MLRegisterModelGroupRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlIndicesHandler = mlIndicesHandler;
        this.threadPool = threadPool;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterModelGroupResponse> listener) {
        MLRegisterModelGroupRequest createModelGroupRequest = MLRegisterModelGroupRequest.fromActionRequest(request);
        MLRegisterModelGroupInput createModelGroupInput = createModelGroupRequest.getRegisterModelGroupInput();
        createModelGroup(
            createModelGroupInput,
            ActionListener
                .wrap(
                    modelGroupId -> { listener.onResponse(new MLRegisterModelGroupResponse(modelGroupId, MLTaskState.CREATED.name())); },
                    ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    }
                )
        );
    }

    public void createModelGroup(MLRegisterModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            User user = RestActionUtils.getUserContext(client);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

                List<String> backendRoles = user.getBackendRoles() != null ? user.getBackendRoles() : ImmutableList.of();

                if (input.getBackendRoles() == null || input.getBackendRoles().size() == 0) {
                    input.setBackendRoles(backendRoles);
                }

                Boolean isRolePresent = input
                    .getBackendRoles()
                    .stream()
                    .allMatch(user.getBackendRoles().stream().collect(Collectors.toSet())::contains);

                if (!isRolePresent) {
                    log.error("Invalid Backend Roles provided in the input");
                    throw new IllegalArgumentException("Invalid Backend Roles provided in the input");
                }

                mlIndicesHandler.initModelGroupIndexIfAbsent(ActionListener.wrap(res -> {
                    MLModelGroup mlModelGroup = MLModelGroup
                        .builder()
                        .name(modelName)
                        .description(input.getDescription())
                        .tags(input.getTags())
                        .backendRoles(input.getBackendRoles())
                        .owner(user)
                        .createdTime(Instant.now())
                        .lastUpdateTime(Instant.now())
                        .build();
                    IndexRequest indexRequest = new IndexRequest(ML_MODEL_GROUP_INDEX);
                    indexRequest
                        .source(mlModelGroup.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                    client.index(indexRequest, ActionListener.wrap(r -> {
                        log.debug("Index model group doc {} successfully created", modelName);
                        listener.onResponse(r.getId());
                    }, e -> {
                        log.error("Failed to index model meta doc", e);
                        listener.onFailure(e);
                    }));
                }, ex -> {
                    log.error("Failed to init model index", ex);
                    listener.onFailure(ex);
                }));
            } catch (Exception e) {
                log.error("Failed to create model meta doc", e);
                listener.onFailure(e);
            }
        } catch (final Exception e) {
            log.error("Failed to init model index", e);
            listener.onFailure(e);
        }
    }

}
