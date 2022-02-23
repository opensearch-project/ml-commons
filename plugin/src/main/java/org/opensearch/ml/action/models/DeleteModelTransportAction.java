/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DeleteModelTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    TransportService transportService;
    Client client;

    @Inject
    public DeleteModelTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
        this.transportService = transportService;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.fromActionRequest(request);
        String modelId = mlModelDeleteRequest.getModelId();

        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_INDEX, modelId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {
                    log.info("Completed Delete Model Request, model id:{} deleted", modelId);
                    actionListener.onResponse(deleteResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete ML model " + modelId, e);
                    actionListener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to delete ML model " + modelId, e);
            actionListener.onFailure(e);
        }
    }

}
