/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetRequest;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModelGroupTransportAction extends HandledTransportAction<ActionRequest, MLModelGroupGetResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ModelAccessControlHelper modelAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelGroupGetAction.NAME, transportService, actionFilters, MLModelGroupGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelGroupGetResponse> actionListener) {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.fromActionRequest(request);
        String modelGroupId = mlModelGroupGetRequest.getModelGroupId();
        String tenantId = mlModelGroupGetRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_MODEL_GROUP_INDEX)
            .id(modelGroupId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModelGroupGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);

            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> handleResponse(r, throwable, modelGroupId, tenantId, user, wrappedListener));
        } catch (Exception e) {
            log.error("Failed to get ML model group {}", modelGroupId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleResponse(
        GetDataObjectResponse getDataObjectResponse,
        Throwable throwable,
        String modelGroupId,
        String tenantId,
        User user,
        ActionListener<MLModelGroupGetResponse> wrappedListener
    ) {
        log.debug("Completed Get Model group Request, id:{}", modelGroupId);
        if (throwable != null) {
            handleThrowable(throwable, modelGroupId, wrappedListener);
        } else {
            processResponse(getDataObjectResponse, modelGroupId, tenantId, user, wrappedListener);
        }
    }

    private void handleThrowable(Throwable throwable, String modelGroupId, ActionListener<MLModelGroupGetResponse> wrappedListener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        if (cause instanceof IndexNotFoundException) {
            log.error("Failed to find model group index", cause);
            wrappedListener.onFailure(new OpenSearchStatusException("Failed to find model group index", RestStatus.NOT_FOUND));
        } else {
            log.error("Failed to get ML group {}", modelGroupId, cause);
            wrappedListener.onFailure(cause);
        }
    }

    private void processResponse(
        GetDataObjectResponse getDataObjectResponse,
        String modelGroupId,
        String tenantId,
        User user,
        ActionListener<MLModelGroupGetResponse> wrappedListener
    ) {
        if (getDataObjectResponse != null && getDataObjectResponse.parser().isPresent()) {
            try {
                XContentParser parser = getDataObjectResponse.parser().get();
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                MLModelGroup mlModelGroup = MLModelGroup.parse(parser);

                if (TenantAwareHelper
                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlModelGroup.getTenantId(), wrappedListener)) {
                    validateModelGroupAccess(user, modelGroupId, mlModelGroup, wrappedListener);
                }
            } catch (Exception e) {
                log.error("Failed to parse ml connector {}", getDataObjectResponse.id(), e);
                wrappedListener.onFailure(e);
            }
        } else {
            wrappedListener
                .onFailure(
                    new OpenSearchStatusException(
                        "Failed to find model group with the provided model group id: " + modelGroupId,
                        RestStatus.NOT_FOUND
                    )
                );
        }
    }

    private void validateModelGroupAccess(
        User user,
        String modelGroupId,
        MLModelGroup mlModelGroup,
        ActionListener<MLModelGroupGetResponse> wrappedListener
    ) {
        modelAccessControlHelper.validateModelGroupAccess(user, modelGroupId, client, ActionListener.wrap(access -> {
            if (!access) {
                wrappedListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have privilege to perform this operation on this model group",
                            RestStatus.FORBIDDEN
                        )
                    );
            } else {
                wrappedListener.onResponse(MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build());
            }
        }, e -> {
            log.error("Failed to validate access for Model Group {}", modelGroupId, e);
            wrappedListener.onFailure(e);
        }));
    }
}
