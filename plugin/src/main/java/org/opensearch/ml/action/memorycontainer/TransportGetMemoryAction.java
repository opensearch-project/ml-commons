/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryGetAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportGetMemoryAction extends HandledTransportAction<ActionRequest, MLMemoryGetResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportGetMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMemoryGetAction.NAME, transportService, actionFilters, MLMemoryGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMemoryGetResponse> actionListener) {
        MLMemoryGetRequest mlMemoryGetRequest = MLMemoryGetRequest.fromActionRequest(request);
        String memoryContainerId = mlMemoryGetRequest.getMemoryContainerId();
        String memoryId = mlMemoryGetRequest.getMemoryID();
        String tenantId = mlMemoryGetRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        // Use transport call to get the memory container
        MLMemoryContainerGetRequest containerRequest = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId(memoryContainerId)
            .tenantId(tenantId)
            .build();

        client.execute(MLMemoryContainerGetAction.INSTANCE, containerRequest, ActionListener.wrap(containerResponse -> {
            // Now get the memory from the container's index
            getMemoryFromContainer(containerResponse.getMlMemoryContainer(), memoryId, tenantId, actionListener);
        }, actionListener::onFailure));
    }

    private void getMemoryFromContainer(
        MLMemoryContainer mlMemoryContainer,
        String memoryId,
        String tenantId,
        ActionListener<MLMemoryGetResponse> actionListener
    ) {
        String memoryIndexName = mlMemoryContainer.getMemoryStorageConfig().getMemoryIndexName();

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getMemoryRequest = GetDataObjectRequest
            .builder()
            .index(memoryIndexName)
            .id(memoryId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        sdkClient
            .getDataObjectAsync(getMemoryRequest)
            .whenComplete((r, throwable) -> handleMemoryResponse(r, throwable, memoryId, actionListener));
    }

    private void handleMemoryResponse(
        GetDataObjectResponse getDataObjectResponse,
        Throwable throwable,
        String memoryId,
        ActionListener<MLMemoryGetResponse> actionListener
    ) {
        log.debug("Completed Get Memory Request, id:{}", memoryId);
        if (throwable != null) {
            handleMemoryThrowable(throwable, memoryId, actionListener);
        } else {
            processMemoryResponse(getDataObjectResponse, memoryId, actionListener);
        }
    }

    private void handleMemoryThrowable(Throwable throwable, String memoryId, ActionListener<MLMemoryGetResponse> actionListener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
            log.error("Failed to find memory index", cause);
            actionListener.onFailure(new OpenSearchStatusException("Failed to find memory index", RestStatus.NOT_FOUND));
        } else {
            log.error("Failed to get ML memory {}", memoryId, cause);
            actionListener.onFailure(cause);
        }
    }

    private void processMemoryResponse(
        GetDataObjectResponse getDataObjectResponse,
        String memoryId,
        ActionListener<MLMemoryGetResponse> actionListener
    ) {
        try {
            GetResponse gr = getDataObjectResponse.getResponse();
            if (gr != null && gr.isExists()) {
                try (
                    XContentParser parser = jsonXContent
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                ) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLMemory mlMemory = MLMemory.parse(parser);
                    actionListener.onResponse(new MLMemoryGetResponse(mlMemory));
                } catch (Exception e) {
                    log.error("Failed to parse ml memory {}", getDataObjectResponse.id(), e);
                    actionListener.onFailure(e);
                }
            } else {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Failed to find memory with the provided memory id: " + memoryId,
                            RestStatus.NOT_FOUND
                        )
                    );
            }
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }
}
