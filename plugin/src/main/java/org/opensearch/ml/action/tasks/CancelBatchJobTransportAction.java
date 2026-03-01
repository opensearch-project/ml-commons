/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.CANCEL_BATCH_PREDICT;
import static org.opensearch.ml.utils.MLExceptionUtils.BATCH_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.core5.http.HttpStatus;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobAction;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobRequest;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.ConnectorUtils;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CancelBatchJobTransportAction extends HandledTransportAction<ActionRequest, MLCancelBatchJobResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;
    ScriptService scriptService;

    ConnectorAccessControlHelper connectorAccessControlHelper;
    ModelAccessControlHelper modelAccessControlHelper;
    EncryptorImpl encryptor;
    MLModelManager mlModelManager;

    MLTaskManager mlTaskManager;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public CancelBatchJobTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ScriptService scriptService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        ModelAccessControlHelper modelAccessControlHelper,
        EncryptorImpl encryptor,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLCancelBatchJobAction.NAME, transportService, actionFilters, MLCancelBatchJobRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.encryptor = encryptor;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCancelBatchJobResponse> actionListener) {
        MLCancelBatchJobRequest mlCancelBatchJobRequest = MLCancelBatchJobRequest.fromActionRequest(request);
        String taskId = mlCancelBatchJobRequest.getTaskId();
        GetRequest getRequest = new GetRequest(ML_TASK_INDEX).id(taskId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                log.debug("Completed Get Task Request, id:{}", taskId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLTask mlTask = MLTask.parse(parser);

                        // check if function is remote and task is of type batch prediction
                        if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION
                            && !mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()) {
                            throw new IllegalStateException(BATCH_INFERENCE_DISABLED_ERR_MSG);
                        }
                        if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION && mlTask.getFunctionName() == FunctionName.REMOTE) {
                            processRemoteBatchPrediction(mlTask, actionListener);
                        } else {
                            actionListener
                                .onFailure(new IllegalArgumentException("The task ID you provided does not have any associated batch job"));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml task {}", r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new OpenSearchStatusException("Fail to find task", RestStatus.NOT_FOUND));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find task"));
                } else {
                    log.error("Failed to get ML task {}", taskId, e);
                    actionListener.onFailure(e);
                }
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to get ML task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void processRemoteBatchPrediction(MLTask mlTask, ActionListener<MLCancelBatchJobResponse> actionListener) {
        Map<String, Object> remoteJob = mlTask.getRemoteJob();

        Map<String, String> parameters = new HashMap<>();
        for (Map.Entry<String, ?> entry : remoteJob.entrySet()) {
            if (entry.getValue() instanceof String) {
                parameters.put(entry.getKey(), (String) entry.getValue());
            } else {
                log.debug("Value for key {} is not a String", entry.getKey());
            }
        }

        // In sagemaker, to retrieve batch transform job details, we need transformJob name. So retrieving name from the arn
        parameters
            .computeIfAbsent(
                "TransformJobName",
                key -> Optional
                    .ofNullable(parameters.get("TransformJobArn"))
                    .map(jobArn -> jobArn.substring(jobArn.lastIndexOf("/") + 1))
                    .orElse(null)
            );

        RemoteInferenceInputDataSet inferenceInputDataSet = new RemoteInferenceInputDataSet(
            parameters,
            ActionType.BATCH_PREDICT_STATUS,
            null
        );
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inferenceInputDataSet).build();
        String modelId = mlTask.getModelId();
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> getModelListener = ActionListener.wrap(model -> {
                modelAccessControlHelper
                    .validateModelGroupAccess(
                        user,
                        model.getModelGroupId(),
                        MLCancelBatchJobAction.NAME,
                        client,
                        ActionListener.wrap(access -> {
                            if (!access) {
                                actionListener.onFailure(new MLValidationException("You don't have permission to cancel this batch job"));
                            } else {
                                if (model.getConnector() != null) {
                                    Connector connector = model.getConnector();
                                    executeConnector(connector, mlInput, actionListener);
                                } else if (MLIndicesHandler
                                    .doesMultiTenantIndexExist(
                                        clusterService,
                                        mlFeatureEnabledSetting.isMultiTenancyEnabled(),
                                        ML_CONNECTOR_INDEX
                                    )) {
                                    ActionListener<Connector> listener = ActionListener
                                        .wrap(connector -> { executeConnector(connector, mlInput, actionListener); }, e -> {
                                            log.error("Failed to get connector {}", model.getConnectorId(), e);
                                            actionListener.onFailure(e);
                                        });
                                    try (
                                        ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()
                                    ) {
                                        connectorAccessControlHelper
                                            .getConnector(
                                                client,
                                                model.getConnectorId(),
                                                ActionListener.runBefore(listener, threadContext::restore)
                                            );
                                    }
                                } else {
                                    actionListener
                                        .onFailure(new ResourceNotFoundException("Can't find connector " + model.getConnectorId()));
                                }
                            }
                        }, e -> {
                            log.error("Failed to validate Access for Model Group " + model.getModelGroupId(), e);
                            actionListener.onFailure(e);
                        })
                    );
            }, e -> {
                log.error("Failed to retrieve the ML model with the given ID", e);
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("Failed to retrieve the ML model for the given task ID", RestStatus.NOT_FOUND)
                    );
            });
            mlModelManager.getModel(modelId, null, null, ActionListener.runBefore(getModelListener, context::restore));
        } catch (Exception e) {
            log.error("Unable to fetch cancel batch job in ml task ", e);
            throw new OpenSearchException("Unable to fetch cancel batch job in ml task " + e.getMessage());
        }
    }

    private void executeConnector(Connector connector, MLInput mlInput, ActionListener<MLCancelBatchJobResponse> actionListener) {
        Optional<ConnectorAction> cancelBatchPredictAction = connector.findAction(CANCEL_BATCH_PREDICT.name());
        if (cancelBatchPredictAction.isEmpty() || cancelBatchPredictAction.get().getRequestBody() == null) {
            ConnectorAction connectorAction = ConnectorUtils.createConnectorAction(connector, CANCEL_BATCH_PREDICT);
            connector.addAction(connectorAction);
        }
        // multi-tenancy isn't implemented in batch, so setting null as tenant by default
        ActionListener<Boolean> decryptSuccessfulListener = ActionListener.wrap(r -> {
            RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                .initInstance(connector.getProtocol(), connector, Connector.class);
            connectorExecutor.setScriptService(scriptService);
            connectorExecutor.setClusterService(clusterService);
            connectorExecutor.setClient(client);
            connectorExecutor.setXContentRegistry(xContentRegistry);
            connectorExecutor.executeAction(CANCEL_BATCH_PREDICT.name(), mlInput, ActionListener.wrap(taskResponse -> {
                processTaskResponse(taskResponse, actionListener);
            }, actionListener::onFailure));
        }, e -> {
            log.error("Failed to decrypt credentials in connector", e);
            actionListener.onFailure(e);
        });
        connector.decrypt(CANCEL_BATCH_PREDICT.name(), encryptor::decrypt, null, decryptSuccessfulListener);
    }

    private void processTaskResponse(MLTaskResponse taskResponse, ActionListener<MLCancelBatchJobResponse> actionListener) {
        try {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) taskResponse.getOutput();
            if (tensorOutput != null && tensorOutput.getMlModelOutputs() != null && !tensorOutput.getMlModelOutputs().isEmpty()) {
                ModelTensors modelOutput = tensorOutput.getMlModelOutputs().get(0);
                if (modelOutput.getStatusCode() != null && modelOutput.getStatusCode().equals(HttpStatus.SC_OK)) {
                    actionListener.onResponse(new MLCancelBatchJobResponse(RestStatus.OK));
                } else {
                    log.debug("The status code from remote service is: {}", modelOutput.getStatusCode());
                    actionListener.onFailure(new OpenSearchException("Couldn't cancel the transform job. Please try again"));
                }
            } else {
                log.debug("ML Model Outputs are null or empty.");
                actionListener.onFailure(new ResourceNotFoundException("Couldn't fetch status of the transform job"));
            }
        } catch (Exception e) {
            log.error("Unable to fetch status for ml task ", e);
        }
    }
}
