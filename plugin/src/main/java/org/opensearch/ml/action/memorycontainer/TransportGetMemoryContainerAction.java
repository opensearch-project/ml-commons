/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportGetMemoryContainerAction extends HandledTransportAction<ActionRequest, MLMemoryContainerGetResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportGetMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMemoryContainerGetAction.NAME, transportService, actionFilters, MLMemoryContainerGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMemoryContainerGetResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = MLMemoryContainerGetRequest.fromActionRequest(request);
        String memoryContainerId = mlMemoryContainerGetRequest.getMemoryContainerId();
        String tenantId = mlMemoryContainerGetRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryContainerGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);

            sdkClient
                .getDataObjectAsync(getDataObjectRequest)
                .whenComplete((r, throwable) -> handleResponse(r, throwable, memoryContainerId, tenantId, user, wrappedListener));
        } catch (Exception e) {
            log.error("Failed to get ML memory container {}", memoryContainerId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleResponse(
        GetDataObjectResponse getDataObjectResponse,
        Throwable throwable,
        String memoryContainerId,
        String tenantId,
        User user,
        ActionListener<MLMemoryContainerGetResponse> wrappedListener
    ) {
        log.debug("Completed Get Memory Container Request, id:{}", memoryContainerId);
        if (throwable != null) {
            handleThrowable(throwable, memoryContainerId, wrappedListener);
        } else {
            processResponse(getDataObjectResponse, memoryContainerId, tenantId, user, wrappedListener);
        }
    }

    private void handleThrowable(
        Throwable throwable,
        String memoryContainerId,
        ActionListener<MLMemoryContainerGetResponse> wrappedListener
    ) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
            log.error("Failed to find memory container index", cause);
            wrappedListener.onFailure(new OpenSearchStatusException("Failed to find memory container index", RestStatus.NOT_FOUND));
        } else {
            log.error("Failed to get ML memory container {}", memoryContainerId, cause);
            wrappedListener.onFailure(cause);
        }
    }

    private void processResponse(
        GetDataObjectResponse getDataObjectResponse,
        String memoryContainerId,
        String tenantId,
        User user,
        ActionListener<MLMemoryContainerGetResponse> wrappedListener
    ) {
        try {
            GetResponse gr = getDataObjectResponse.getResponse();
            if (gr != null && gr.isExists()) {
                try (
                    XContentParser parser = jsonXContent
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                ) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLMemoryContainer mlMemoryContainer = MLMemoryContainer.parse(parser);

                    if (TenantAwareHelper
                        .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlMemoryContainer.getTenantId(), wrappedListener)) {
                        validateMemoryContainerAccess(user, memoryContainerId, mlMemoryContainer, wrappedListener);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse ml memory container {}", getDataObjectResponse.id(), e);
                    wrappedListener.onFailure(e);
                }
            } else {
                wrappedListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Failed to find memory container with the provided memory container id: " + memoryContainerId,
                            RestStatus.NOT_FOUND
                        )
                    );
            }
        } catch (Exception e) {
            wrappedListener.onFailure(e);
        }
    }

    private void validateMemoryContainerAccess(
        User user,
        String memoryContainerId,
        MLMemoryContainer mlMemoryContainer,
        ActionListener<MLMemoryContainerGetResponse> wrappedListener
    ) {
        // Check if user has access based on owner field
        boolean hasAccess = checkMemoryContainerAccess(user, mlMemoryContainer);

        if (!hasAccess) {
            wrappedListener
                .onFailure(
                    new OpenSearchStatusException(
                        "User doesn't have privilege to perform this operation on this memory container",
                        RestStatus.FORBIDDEN
                    )
                );
        } else {
            wrappedListener.onResponse(MLMemoryContainerGetResponse.builder().mlMemoryContainer(mlMemoryContainer).build());
        }
    }

    @VisibleForTesting
    boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }
}
