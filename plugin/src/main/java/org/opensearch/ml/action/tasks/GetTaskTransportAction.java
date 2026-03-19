/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.REMOTE_JOB_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.CANCELLED;
import static org.opensearch.ml.common.MLTaskState.CANCELLING;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.EXPIRED;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.common.MLTaskState.UNREACHABLE;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SESSION_TOKEN_FIELD;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.BATCH_PREDICT_STATUS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_FAILED_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_JOB_STATUS_FIELD;
import static org.opensearch.ml.utils.MLExceptionUtils.BATCH_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
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
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.ConnectorUtils;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.utils.S3Utils;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.script.ScriptService;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.CollectionUtils;

@Log4j2
public class GetTaskTransportAction extends HandledTransportAction<ActionRequest, MLTaskGetResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;
    ScriptService scriptService;

    ConnectorAccessControlHelper connectorAccessControlHelper;
    ModelAccessControlHelper modelAccessControlHelper;
    EncryptorImpl encryptor;
    MLModelManager mlModelManager;

    MLTaskManager mlTaskManager;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    volatile List<String> remoteJobStatusFields;
    volatile Pattern remoteJobCompletedStatusRegexPattern;
    volatile Pattern remoteJobCancelledStatusRegexPattern;
    volatile Pattern remoteJobCancellingStatusRegexPattern;
    volatile Pattern remoteJobExpiredStatusRegexPattern;
    volatile Pattern remoteJobFailedStatusRegexPattern;
    private final MLEngine mlEngine;

    // private Map<String, String> decryptedCredential;

    @Inject
    public GetTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ScriptService scriptService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        ModelAccessControlHelper modelAccessControlHelper,
        EncryptorImpl encryptor,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Settings settings,
        MLEngine mlEngine
    ) {
        super(MLTaskGetAction.NAME, transportService, actionFilters, MLTaskGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.scriptService = scriptService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.encryptor = encryptor;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlEngine = mlEngine;

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
        initializeRegexPattern(
            ML_COMMONS_REMOTE_JOB_STATUS_FAILED_REGEX,
            settings,
            clusterService,
            (regex) -> remoteJobFailedStatusRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
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
        Boolean isUserInitiatedGetTaskRequest = mlTaskGetRequest.isUserInitiatedGetTaskRequest();

        String tenantId = mlTaskGetRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_TASK_INDEX)
            .id(taskId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                handleAsyncResponse(r, throwable, taskId, isUserInitiatedGetTaskRequest, tenantId, actionListener);
            });
        } catch (Exception e) {
            log.error("Failed to get ML task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleAsyncResponse(
        GetDataObjectResponse response,
        Throwable throwable,
        String taskId,
        Boolean isUserInitiatedGetTaskRequest,
        String tenantId,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        log.debug("Completed Get task Request, id:{}", taskId);

        if (throwable != null) {
            handleThrowable(throwable, taskId, actionListener);
            return;
        }
        processResponse(response, taskId, isUserInitiatedGetTaskRequest, tenantId, actionListener);
    }

    private void handleThrowable(Throwable throwable, String taskId, ActionListener<MLTaskGetResponse> actionListener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);

        if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
            log.error("Failed to get task index", cause);
            actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND));
        } else {
            log.error("Failed to get ML task {}", taskId, cause);
            actionListener.onFailure(cause);
        }
    }

    private void processResponse(
        GetDataObjectResponse response,
        String taskId,
        Boolean isUserInitiatedGetTaskRequest,
        String tenantId,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        try {
            GetResponse gr = response.getResponse();

            if (gr == null || !gr.isExists()) {
                actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND));
                return;
            }

            parseAndHandleTask(gr, taskId, isUserInitiatedGetTaskRequest, tenantId, actionListener);
        } catch (Exception e) {
            log.error("Failed to parse GetDataObjectResponse for task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void parseAndHandleTask(
        GetResponse gr,
        String taskId,
        Boolean isUserInitiatedGetTaskRequest,
        String tenantId,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        try (
            XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
        ) {

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            MLTask mlTask = MLTask.parse(parser);

            if (!TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, mlTask.getTenantId(), actionListener)) {
                return;
            }

            if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION && !mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()) {
                throw new IllegalStateException(BATCH_INFERENCE_DISABLED_ERR_MSG);
            }

            if (mlTask.getTaskType() == MLTaskType.BATCH_PREDICTION && mlTask.getFunctionName() == FunctionName.REMOTE) {
                processRemoteBatchPrediction(mlTask, taskId, isUserInitiatedGetTaskRequest, tenantId, actionListener);
            } else {
                actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
            }
        } catch (Exception e) {
            log.error("Failed to parse ML task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void processRemoteBatchPrediction(
        MLTask mlTask,
        String taskId,
        Boolean isUserInitiatedGetTaskRequest,
        String tenantId,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
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
            ActionListener<MLModel> getModelListener = ActionListener
                .wrap(
                    model -> modelAccessControlHelper
                        .validateModelGroupAccess(
                            user,
                            mlFeatureEnabledSetting,
                            tenantId,
                            model.getModelGroupId(),
                            MLTaskGetAction.NAME,
                            client,
                            sdkClient,
                            ActionListener.wrap(access -> {
                                if (!access) {
                                    actionListener
                                        .onFailure(new MLValidationException("You don't have permission to access this batch job"));
                                } else {
                                    if (model.getConnector() != null) {
                                        Connector connector = model.getConnector();
                                        executeConnector(
                                            connector,
                                            mlInput,
                                            taskId,
                                            isUserInitiatedGetTaskRequest,
                                            mlTask,
                                            remoteJob,
                                            actionListener
                                        );
                                    } else if (MLIndicesHandler
                                        .doesMultiTenantIndexExist(
                                            clusterService,
                                            mlFeatureEnabledSetting.isMultiTenancyEnabled(),
                                            ML_CONNECTOR_INDEX
                                        )) {
                                        ActionListener<Connector> listener = ActionListener.wrap(connector -> {
                                            executeConnector(
                                                connector,
                                                mlInput,
                                                taskId,
                                                isUserInitiatedGetTaskRequest,
                                                mlTask,
                                                remoteJob,
                                                actionListener
                                            );
                                        }, e -> {
                                            log.error("Failed to get connector {}", model.getConnectorId(), e);
                                            actionListener.onFailure(e);
                                        });
                                        try (
                                            ThreadContext.StoredContext threadContext = client
                                                .threadPool()
                                                .getThreadContext()
                                                .stashContext()
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
                                log.error("Failed to validate Access for Model Group {}", model.getModelGroupId(), e);
                                actionListener.onFailure(e);
                            })
                        ),
                    e -> {
                        log.error("Failed to retrieve the ML model for the given task ID", e);
                        actionListener
                            .onFailure(
                                new OpenSearchStatusException("Failed to retrieve the ML model for the given task ID", RestStatus.NOT_FOUND)
                            );
                    }
                );
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
        Boolean isUserInitiatedGetTaskRequest,
        MLTask mlTask,
        Map<String, Object> remoteJob,
        ActionListener<MLTaskGetResponse> actionListener
    ) {
        Optional<ConnectorAction> batchPredictStatusAction = connector.findAction(BATCH_PREDICT_STATUS.name());
        if (batchPredictStatusAction.isEmpty() || batchPredictStatusAction.get().getRequestBody() == null) {
            ConnectorAction connectorAction = ConnectorUtils.createConnectorAction(connector, BATCH_PREDICT_STATUS);
            connector.addAction(connectorAction);
        }

        ActionListener<Map<String, String>> decryptedCredentialFetchListener = ActionListener.wrap(r -> {
            RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                .initInstance(connector.getProtocol(), connector, Connector.class);
            connectorExecutor.setScriptService(scriptService);
            connectorExecutor.setClusterService(clusterService);
            connectorExecutor.setClient(client);
            connectorExecutor.setXContentRegistry(xContentRegistry);
            connectorExecutor.executeAction(BATCH_PREDICT_STATUS.name(), mlInput, ActionListener.wrap(taskResponse -> {
                processTaskResponse(mlTask, taskId, isUserInitiatedGetTaskRequest, taskResponse, remoteJob, r, actionListener);
            }, e -> {
                // When the request to remote service fails, we will retry the request for next 10 minutes (10 runs).
                // If it fails even then, we mark it as unreachable in task index and send message to DLQ
                if (!isUserInitiatedGetTaskRequest) {
                    Map<String, Object> updatedTask = new HashMap<>();
                    Integer numberOfRetries = (Integer) remoteJob.getOrDefault("num_of_retries", 0);
                    remoteJob.put("num_of_retries", ++numberOfRetries);
                    if (numberOfRetries > 10) {
                        log
                            .debug(
                                "Limit exceeded trying to reach the task {} . Marking as UNREACHABLE in task index and removing from further execution",
                                taskId
                            );
                        updatedTask.put(STATE_FIELD, UNREACHABLE);
                        mlTask.setState(UNREACHABLE);
                        mlTask.setError(e.getMessage());
                        updateDLQ(mlTask, r);
                    }
                    updatedTask.put("remote_job", remoteJob);
                    mlTaskManager.updateMLTaskDirectly(taskId, updatedTask);
                }
                actionListener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to decrypt credentials in connector", e);
            actionListener.onFailure(e);
        });
        if (CollectionUtils.isNotEmpty(connector.getDecryptedCredential())) {
            decryptedCredentialFetchListener.onResponse(connector.getDecryptedCredential());
        } else {
            mlEngine.getConnectorCredential(connector, decryptedCredentialFetchListener);
        }
    }

    protected void processTaskResponse(
        MLTask mlTask,
        String taskId,
        Boolean isUserInitiatedGetTaskRequest,
        MLTaskResponse taskResponse,
        Map<String, Object> remoteJob,
        Map<String, String> decryptedCredential,
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
                        mlTask.setRemoteJob(remoteJob);

                        for (String statusField : remoteJobStatusFields) {
                            String statusValue = String.valueOf(remoteJob.get(statusField));
                            if (remoteJob.containsKey(statusField)) {
                                updateTaskState(updatedTask, mlTask, statusValue);
                            }
                        }

                        mlTaskManager.updateMLTaskDirectly(taskId, updatedTask, ActionListener.wrap(response -> {
                            if (mlTask.getState().equals(FAILED) && !isUserInitiatedGetTaskRequest) {
                                updateDLQ(mlTask, decryptedCredential);
                            }
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

    @VisibleForTesting
    protected void updateDLQ(MLTask mlTask, Map<String, String> decryptedCredential) {
        Map<String, Object> remoteJob = mlTask.getRemoteJob();
        Map<String, String> dlq = (Map<String, String>) remoteJob.get("dlq");
        if (dlq != null && !dlq.isEmpty()) {
            String taskId = mlTask.getTaskId();
            try {
                Map<String, Object> remoteJobDetails = mlTask.getRemoteJob();
                String accessKey = decryptedCredential.get(ACCESS_KEY_FIELD);
                String secretKey = decryptedCredential.get(SECRET_KEY_FIELD);
                String sessionToken = decryptedCredential.get(SESSION_TOKEN_FIELD);

                String bucketName = dlq.get("bucket");
                String region = dlq.get("region");

                if (bucketName == null || region == null) {
                    log.error("Failed to get the bucket name and region from batch predict request");
                }
                remoteJobDetails.remove("dlq");
                try (S3Client s3Client = S3Utils.initS3Client(accessKey, secretKey, sessionToken, region)) {
                    String jobName = (String) remoteJobDetails.getOrDefault("TransformJobName", remoteJob.get("job_name"));
                    String s3ObjectKey = "BatchJobFailure_" + jobName;
                    String content = mlTask.getState().equals(UNREACHABLE)
                        ? String.format("Unable to reach the Job: %s. Error Message: %s", jobName, mlTask.getError())
                        : remoteJobDetails.toString();

                    S3Utils.putObject(s3Client, bucketName, s3ObjectKey, content);
                    log.debug("Task status successfully uploaded to S3 for task ID: {} at {}", taskId, Instant.now());
                }
            } catch (S3Exception e) {
                log.error("Failed to update task status for task: {}. S3 Exception: {}", taskId, e.awsErrorDetails().errorMessage());
            } catch (Exception e) {
                log.error("Failed to update task status for task: " + taskId, e);
            }
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
        } else if (matchesPattern(remoteJobFailedStatusRegexPattern, statusValue)) {
            updatedTask.put(STATE_FIELD, FAILED);
            mlTask.setState(FAILED);
        }
    }

    private boolean matchesPattern(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }
}
