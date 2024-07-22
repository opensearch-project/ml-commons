/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TRANSFORM_JOB_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_STATUS;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.*;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.PredictMode;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.action.support.ThreadedActionListener;


import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class GetTaskTransportAction extends HandledTransportAction<ActionRequest, MLTaskGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;
    ScriptService scriptService;

    ConnectorAccessControlHelper connectorAccessControlHelper;
    EncryptorImpl encryptor;
    MLModelManager mlModelManager;

    MLTaskManager mlTaskManager;
    MLModelCacheHelper modelCacheHelper;

    @Inject
    public GetTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ScriptService scriptService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        EncryptorImpl encryptor,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager
    ) {
        super(MLTaskGetAction.NAME, transportService, actionFilters, MLTaskGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.encryptor = encryptor;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
    }
    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskGetResponse> actionListener) {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.fromActionRequest(request);
        String taskId = mlTaskGetRequest.getTaskId();
        GetRequest getRequest = new GetRequest(ML_TASK_INDEX).id(taskId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                log.debug("Completed Get Task Request, id:{}", taskId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLTask mlTask = MLTask.parse(parser);

                        // check if function is remote and task is of type batch prediction
                        if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION && mlTask.getFunctionName() == FunctionName.REMOTE) {
                            processRemoteBatchPrediction(mlTask, taskId, actionListener);
                        } else {
                            actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml task " + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new OpenSearchStatusException("Fail to find task", RestStatus.NOT_FOUND));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find task"));
                } else {
                    log.error("Failed to get ML task " + taskId, e);
                    actionListener.onFailure(e);
                }
            }), () -> context.restore()));
        } catch (Exception e) {
            log.error("Failed to get ML task " + taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void processRemoteBatchPrediction(MLTask mlTask, String taskId, ActionListener<MLTaskGetResponse> actionListener) {


        Map<String, Object> transformJob = mlTask.getTransformJob();

        Map<String, String> parameters = new HashMap<>();
        for (Map.Entry<String, ?> entry : transformJob.entrySet()) {
            if (entry.getValue() instanceof String) {
                parameters.put(entry.getKey(), (String) entry.getValue());
            } else {
                log.debug("Value for key " + entry.getKey() + " is not a String");
            }
        }

        if (parameters.containsKey("TransformJobArn") && parameters.get("TransformJobArn") != null) {
            String jobArn = parameters.get("TransformJobArn");
            String transformJobName = jobArn.substring(jobArn.lastIndexOf("/") + 1);
            parameters.put("TransformJobName", transformJobName);
            parameters.remove("TransformJobArn");
        }

        RemoteInferenceInputDataSet inferenceInputDataSet = new RemoteInferenceInputDataSet(parameters, PredictMode.BATCH);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inferenceInputDataSet).build();
        String modelId = mlTask.getModelId();

        try {
            mlModelManager.getModel(modelId, null, null, ActionListener.wrap(model -> {
                if (clusterService.state().metadata().hasIndex(ML_CONNECTOR_INDEX)) {
                    ActionListener<Connector> listener = ActionListener.wrap(connector -> {
                        connector.decrypt(BATCH_STATUS.name(), (credential) -> encryptor.decrypt(credential));
                        RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                            .initInstance(connector.getProtocol(), connector, Connector.class);
                        connectorExecutor.setScriptService(scriptService);
                        connectorExecutor.setClusterService(clusterService);
                        connectorExecutor.setClient(client);
                        connectorExecutor.setXContentRegistry(xContentRegistry);
                        connectorExecutor.executeAction(BATCH_STATUS.name(), mlInput, ActionListener.wrap(taskResponse -> {
                            processTaskResponse(mlTask, taskId, taskResponse, transformJob, actionListener);
                        }, e -> { actionListener.onFailure(e); }));
                    }, e -> {
                        log.error("Failed to get connector " + model.getConnectorId(), e);
                        actionListener.onFailure(e);
                    });
                    try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                        connectorAccessControlHelper
                            .getConnector(client, model.getConnectorId(), ActionListener.runBefore(listener, threadContext::restore));
                    }
                } else {
                    actionListener.onFailure(new ResourceNotFoundException("Can't find connector " + model.getConnectorId()));
                }
            }, e -> {
                log.error("Failed to retrieve the ML model with the given ID", e);
                actionListener.onFailure(e);
            }));
        } catch (Exception e) {
            // fetch the connector
            log.error("Unable to fetch status for ml task ", e);
        }
    }


    private void processTaskResponse(
        MLTask mlTask,
        String taskId,
        MLTaskResponse taskResponse,
        Map<String, Object> transformJob,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        try {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) taskResponse.getOutput();
            if (tensorOutput != null && tensorOutput.getMlModelOutputs() != null && !tensorOutput.getMlModelOutputs().isEmpty()) {
                ModelTensors modelOutput = tensorOutput.getMlModelOutputs().get(0);
                if (modelOutput.getMlModelTensors() != null && !modelOutput.getMlModelTensors().isEmpty()) {
                    Map<String, Object> transformJobStatus = (Map<String, Object>) modelOutput.getMlModelTensors().get(0).getDataAsMap();
                    if (transformJobStatus != null) {
                        transformJob.putAll(transformJobStatus);
                        Map<String, Object> updatedTask = new HashMap<>();
                        updatedTask.put(TRANSFORM_JOB_FIELD, transformJob);

                        if ((transformJob.containsKey("status") && transformJob.get("status").equals("completed"))
                            || (transformJob.containsKey("TransformJobStatus")
                                && transformJob.get("TransformJobStatus").equals("Completed"))) {
                            updatedTask.put(STATE_FIELD, COMPLETED);
                            mlTask.setState(COMPLETED);
                        }
                        mlTaskManager.updateMLTaskDirectly(taskId, updatedTask, ActionListener.wrap(response -> {
                            actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
                        }, e -> {
                            logException("Failed to update task for batch predict model", e, log);
                            actionListener.onFailure(e);
                        }));
                    } else {
                        log.debug("Transform job status is null.");
                        actionListener.onFailure(new ResourceNotFoundException("Couldn't fetch status of the transform job"));
                    }
                } else {
                    log.debug("ML Model Tensors are null or empty.");
                    actionListener.onFailure(new ResourceNotFoundException("Couldn't fetch status of the transform job"));
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

