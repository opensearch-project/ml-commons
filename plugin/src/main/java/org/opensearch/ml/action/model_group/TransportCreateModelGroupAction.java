/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLCreateModelGroupResponse;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportCreateModelGroupAction extends HandledTransportAction<ActionRequest, MLCreateModelGroupResponse> {

    private final TransportService transportService;
    private final ActionFilters actionFilters;
    private final MLIndicesHandler mlIndicesHandler;
    private final ThreadPool threadPool;
    private final Client client;

    @Inject
    public TransportCreateModelGroupAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        ThreadPool threadPool,
        Client client
    ) {
        super(MLCreateModelGroupAction.NAME, transportService, actionFilters, MLCreateModelGroupRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlIndicesHandler = mlIndicesHandler;
        this.threadPool = threadPool;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateModelGroupResponse> listener) {
        MLCreateModelGroupRequest createModelGroupRequest = MLCreateModelGroupRequest.fromActionRequest(request);
        MLCreateModelGroupInput createModelGroupInput = createModelGroupRequest.getCreateModelGroupInput();
        createModelGroup(
            createModelGroupInput,
            ActionListener
                .wrap(
                    modelGroupId -> { listener.onResponse(new MLCreateModelGroupResponse(modelGroupId, MLTaskState.CREATED.name())); },
                    ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    }
                )
        );
    }

    public void createModelGroup(MLCreateModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                mlIndicesHandler.initModelGroupIndexIfAbsent(ActionListener.wrap(res -> {
                    MLModelGroup mlModelMeta = MLModelGroup.builder().name(modelName).description(input.getDescription()).build();
                    IndexRequest indexRequest = new IndexRequest(ML_MODEL_GROUP_INDEX);
                    indexRequest
                        .source(mlModelMeta.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                    client.index(indexRequest, ActionListener.wrap(r -> {
                        log.debug("Index model meta doc successfully {}", modelName);
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
