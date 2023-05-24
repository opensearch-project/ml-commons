/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportRegisterModelMetaAction extends HandledTransportAction<ActionRequest, MLRegisterModelMetaResponse> {

    TransportService transportService;
    ActionFilters actionFilters;
    MLModelManager mlModelManager;
    Client client;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportRegisterModelMetaAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelManager mlModelManager,
        Client client,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLRegisterModelMetaAction.NAME, transportService, actionFilters, MLRegisterModelMetaRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlModelManager = mlModelManager;
        this.client = client;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterModelMetaResponse> listener) {
        MLRegisterModelMetaRequest registerModelMetaRequest = MLRegisterModelMetaRequest.fromActionRequest(request);
        MLRegisterModelMetaInput mlUploadInput = registerModelMetaRequest.getMlRegisterModelMetaInput();

        User user = RestActionUtils.getUserContext(client);

        modelAccessControlHelper.validateModelGroupAccess(user, mlUploadInput.getModelGroupId(), client, ActionListener.wrap(access -> {
            if (!access) {
                log.error("User doesn't have valid privilege to perform this operation on this model");
                listener
                    .onFailure(new IllegalArgumentException("User doesn't have valid privilege to perform this operation on this model"));
            } else {
                mlModelManager
                    .registerModelMeta(
                        mlUploadInput,
                        ActionListener
                            .wrap(
                                modelId -> { listener.onResponse(new MLRegisterModelMetaResponse(modelId, MLTaskState.CREATED.name())); },
                                ex -> {
                                    log.error("Failed to init model index", ex);
                                    listener.onFailure(ex);
                                }
                            )
                    );
            }
        }, e -> {
            logException("Failed to validate model access", e, log);
            listener.onFailure(e);
        }));
    }
}
