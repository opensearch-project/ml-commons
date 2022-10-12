/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DeleteModelTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    static final String TIMEOUT_MSG = "Timeout while deleting model of ";
    static final String BULK_FAILURE_MSG = "Bulk failure while deleting model of ";
    static final String SEARCH_FAILURE_MSG = "Search failure while deleting model of ";
    static final String OS_STATUS_EXCEPTION_MESSAGE = "Failed to delete all model chunks ";
    Client client;

    @Inject
    public DeleteModelTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
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
                    deleteModelChunks(modelId, deleteResponse, actionListener);
                    log.info("Completed Delete Model Request for model id:{} ", modelId);
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

    @VisibleForTesting
    void deleteModelChunks(String modelId, DeleteResponse deleteResponse, ActionListener<DeleteResponse> actionListener) {
        DeleteByQueryRequest deleteModelsRequest = new DeleteByQueryRequest(ML_MODEL_INDEX);
        deleteModelsRequest.setQuery(new TermsQueryBuilder(MODEL_ID_FIELD, modelId));

        client.execute(DeleteByQueryAction.INSTANCE, deleteModelsRequest, ActionListener.wrap(r -> {
            if ((r.getBulkFailures() == null || r.getBulkFailures().size() == 0)
                && (r.getSearchFailures() == null || r.getSearchFailures().size() == 0)) {
                log.info("All model chunks are deleted for model {}", modelId);
                actionListener.onResponse(deleteResponse);
            } else {
                logFailure(r, modelId, actionListener);
            }
        }, e -> {
            log.info("Failed to delete ML model for " + modelId, e);
            actionListener.onFailure(e);
        }));
    }

    private void logFailure(BulkByScrollResponse response, String modelId, ActionListener<DeleteResponse> actionListener) {
        String errorMessage = "";
        if (response.isTimedOut()) {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + "," + TIMEOUT_MSG + modelId;
        } else if (!response.getBulkFailures().isEmpty()) {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + "," + BULK_FAILURE_MSG + modelId;
        } else {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + "," + SEARCH_FAILURE_MSG + modelId;
        }
        log.info(response.toString());
        actionListener.onFailure(new OpenSearchStatusException(errorMessage, RestStatus.INTERNAL_SERVER_ERROR));
    }
}
