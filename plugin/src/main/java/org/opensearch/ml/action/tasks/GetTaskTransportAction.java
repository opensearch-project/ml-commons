/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.REMOTE_JOB_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.CANCELLED;
import static org.opensearch.ml.common.MLTaskState.CANCELLING;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.EXPIRED;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_PREDICT_STATUS;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_FIELD;
import static org.opensearch.ml.utils.MLExceptionUtils.BATCH_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
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
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
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
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

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
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    volatile List<String> remoteJobStatusFields;
    volatile Pattern remoteJobCompletedStatusRegexPattern;
    volatile Pattern remoteJobCancelledStatusRegexPattern;
    volatile Pattern remoteJobCancellingStatusRegexPattern;
    volatile Pattern remoteJobExpiredStatusRegexPattern;

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
        MLModelManager mlModelManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Settings settings
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
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;

        remoteJobStatusFields = ML_COMMONS_REMOTE_JOB_STATUS_FIELD.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_REMOTE_JOB_STATUS_FIELD, it -> remoteJobStatusFields = it);
        initializeRegexPattern(
            ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX,
            settings,
            clusterService,
            (regex) -> remoteJobCompletedStatusRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        );
        initializeRegexPattern(
            ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX,
            settings,
            clusterService,
            (regex) -> remoteJobCancelledStatusRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        );
        initializeRegexPattern(
            ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX,
            settings,
            clusterService,
            (regex) -> remoteJobCancellingStatusRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        );
        initializeRegexPattern(
            ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX,
            settings,
            clusterService,
            (regex) -> remoteJobExpiredStatusRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        );
    }

    private void initializeRegexPattern(
        Setting<String> setting,
        Settings settings,
        ClusterService clusterService,
        Consumer<String> patternInitializer
    ) {
        String regex = setting.get(settings);
        patternInitializer.accept(regex);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(setting, it -> patternInitializer.accept(it));
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
                        if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION
                            && !mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()) {
                            throw new IllegalStateException(BATCH_INFERENCE_DISABLED_ERR_MSG);
                        }
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
        Map<String, Object> remoteJob = mlTask.getRemoteJob();

        Map<String, String> parameters = new HashMap<>();
        for (Map.Entry<String, ?> entry : remoteJob.entrySet()) {
            if (entry.getValue() instanceof String) {
                parameters.put(entry.getKey(), (String) entry.getValue());
            } else {
                log.debug("Value for key " + entry.getKey() + " is not a String");
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

        RemoteInferenceInputDataSet inferenceInputDataSet = new RemoteInferenceInputDataSet(parameters, ActionType.BATCH_PREDICT_STATUS);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inferenceInputDataSet).build();
        String modelId = mlTask.getModelId();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> getModelListener = ActionListener.wrap(model -> {
                if (model.getConnector() != null) {
                    Connector connector = model.getConnector();
                    executeConnector(connector, mlInput, taskId, mlTask, remoteJob, actionListener);
                } else if (clusterService.state().metadata().hasIndex(ML_CONNECTOR_INDEX)) {
                    ActionListener<Connector> listener = ActionListener.wrap(connector -> {
                        executeConnector(connector, mlInput, taskId, mlTask, remoteJob, actionListener);
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
                log.error("Failed to retrieve the ML model for the given task ID", e);
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("Failed to retrieve the ML model for the given task ID", RestStatus.NOT_FOUND)
                    );
            });
            mlModelManager.getModel(modelId, null, null, ActionListener.runBefore(getModelListener, context::restore));
        } catch (Exception e) {
            log.error("Unable to fetch status for ml task ", e);
            throw new OpenSearchException("Unable to fetch status for ml task " + e.getMessage());
        }
    }

    private void executeConnector(
        Connector connector,
        MLInput mlInput,
        String taskId,
        MLTask mlTask,
        Map<String, Object> remoteJob,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        if (connectorAccessControlHelper.validateConnectorAccess(client, connector)) {
            connector.decrypt(BATCH_PREDICT_STATUS.name(), (credential) -> encryptor.decrypt(credential));
            RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                .initInstance(connector.getProtocol(), connector, Connector.class);
            connectorExecutor.setScriptService(scriptService);
            connectorExecutor.setClusterService(clusterService);
            connectorExecutor.setClient(client);
            connectorExecutor.setXContentRegistry(xContentRegistry);
            connectorExecutor.executeAction(BATCH_PREDICT_STATUS.name(), mlInput, ActionListener.wrap(taskResponse -> {
                processTaskResponse(mlTask, taskId, taskResponse, remoteJob, actionListener);
            }, e -> { actionListener.onFailure(e); }));
        } else {
            actionListener
                .onFailure(new OpenSearchStatusException("You don't have permission to access this connector", RestStatus.FORBIDDEN));
        }
    }

    protected void processTaskResponse(
        MLTask mlTask,
        String taskId,
        MLTaskResponse taskResponse,
        Map<String, Object> remoteJob,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        try {
            ModelTensorOutput tensorOutput = (ModelTensorOutput) taskResponse.getOutput();
            if (tensorOutput != null && tensorOutput.getMlModelOutputs() != null && !tensorOutput.getMlModelOutputs().isEmpty()) {
                ModelTensors modelOutput = tensorOutput.getMlModelOutputs().get(0);
                if (modelOutput.getMlModelTensors() != null && !modelOutput.getMlModelTensors().isEmpty()) {
                    Map<String, Object> remoteJobStatus = (Map<String, Object>) modelOutput.getMlModelTensors().get(0).getDataAsMap();
                    if (remoteJobStatus != null) {
                        remoteJob.putAll(remoteJobStatus);
                        Map<String, Object> updatedTask = new HashMap<>();
                        updatedTask.put(REMOTE_JOB_FIELD, remoteJob);

                        for (String statusField : remoteJobStatusFields) {
                            String statusValue = String.valueOf(remoteJob.get(statusField));
                            if (remoteJob.containsKey(statusField)) {
                                updateTaskState(updatedTask, mlTask, statusValue);
                            }
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

    private void updateTaskState(Map<String, Object> updatedTask, MLTask mlTask, String statusValue) {
        if (matchesPattern(remoteJobCancellingStatusRegexPattern, statusValue)) {
            updatedTask.put(STATE_FIELD, CANCELLING);
            mlTask.setState(CANCELLING);
        } else if (matchesPattern(remoteJobCancelledStatusRegexPattern, statusValue)) {
            updatedTask.put(STATE_FIELD, CANCELLED);
            mlTask.setState(CANCELLED);
        } else if (matchesPattern(remoteJobCompletedStatusRegexPattern, statusValue)) {
            updatedTask.put(STATE_FIELD, COMPLETED);
            mlTask.setState(COMPLETED);
        } else if (matchesPattern(remoteJobExpiredStatusRegexPattern, statusValue)) {
            updatedTask.put(STATE_FIELD, EXPIRED);
            mlTask.setState(EXPIRED);
        }
    }

    private boolean matchesPattern(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }
}
