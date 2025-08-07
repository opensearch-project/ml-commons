package org.opensearch.ml.action.IndexInsight;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.IndexInsightContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.FIXED_INDEX_INSIGHT_CONTAINER_ID;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONTAINER_INDEX;

@Getter
@Log4j2
public class DeleteIndexInsightContainerTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightContainerDeleteResponse> {
    private Client client;
    private final SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLIndicesHandler mlIndicesHandler;

    @Inject
    public DeleteIndexInsightContainerTransportAction(TransportService transportService,
                                                   ActionFilters actionFilters,
                                                   NamedXContentRegistry xContentRegistry,
                                                   MLFeatureEnabledSetting mlFeatureEnabledSetting,
                                                   Client client, SdkClient sdkClient, MLIndicesHandler mlIndicesHandler) {
        super(MLIndexInsightContainerDeleteAction.NAME, transportService, actionFilters, MLIndexInsightContainerDeleteRequest::new);
        this.client = client;

        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    private void deleteOriginalIndexInsightIndex(String indexName, ActionListener<Boolean> listener) {
        client.admin().indices().delete(
                new DeleteIndexRequest(indexName),
                ActionListener.wrap(r -> {
                    if (r.isAcknowledged()) {
                        listener.onResponse(true);
                    } else {
                        listener.onFailure(new RuntimeException("Failed to delete original index insight data index: " + indexName));
                    }
                }, listener::onFailure)
        );
    }

    private void getIndexInsightContainer(String tenantId, ActionListener<String> listener){
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                    .getDataObjectAsync(
                            GetDataObjectRequest
                                    .builder()
                                    .tenantId(tenantId)
                                    .index(ML_INDEX_INSIGHT_CONTAINER_INDEX)
                                    .id(FIXED_INDEX_INSIGHT_CONTAINER_ID)
                                    .build()
                    )
                    .whenComplete((r, throwable) -> {
                        context.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            listener.onFailure(cause);
                        } else {
                            try {
                                GetResponse getResponse = r.getResponse();
                                if (getResponse.isExists()) {
                                    try (XContentParser parser = jsonXContent
                                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())) {
                                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                        IndexInsightContainer indexInsightContainer = IndexInsightContainer.parse(parser);
                                        listener.onResponse(indexInsightContainer.getIndexName());
                                    } catch (Exception e) {
                                        listener.onFailure(e);
                                    }
                                } else {
                                    listener.onFailure(new RuntimeException("The container is not set yet"));
                                }

                            } catch (Exception e) {
                                listener.onFailure(e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to create index insight container", e);
            listener.onFailure(e);
        }
    }


    private void deleteIndexInsightContainer(String tenantId, ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                    .deleteDataObjectAsync(
                            DeleteDataObjectRequest
                                    .builder()
                                    .tenantId(tenantId)
                                    .index(ML_INDEX_INSIGHT_CONTAINER_INDEX)
                                    .id(FIXED_INDEX_INSIGHT_CONTAINER_ID)
                                    .build()
                    )
                    .whenComplete((r, throwable) -> {
                        context.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            listener.onFailure(cause);
                        } else {
                            try {
                                DeleteResponse deleteResponse = r.deleteResponse();
                                if (deleteResponse.status() == RestStatus.ACCEPTED) {
                                    listener.onResponse(true);
                                } else {
                                    listener.onFailure(new RuntimeException("The container is not set yet"));
                                }

                            } catch (Exception e) {
                                listener.onFailure(e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to create index insight container", e);
            listener.onFailure(e);
        }
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightContainerDeleteResponse> listener) {
        MLIndexInsightContainerDeleteRequest mlIndexInsightContainerDeleteRequest = MLIndexInsightContainerDeleteRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightContainerDeleteRequest.getTenantId(), listener)) {
            return;
        }
        String tenantId = mlIndexInsightContainerDeleteRequest.getTenantId();
        getIndexInsightContainer(tenantId, ActionListener.wrap(indexName -> {
            deleteOriginalIndexInsightIndex(indexName, ActionListener.wrap(r -> {
                deleteIndexInsightContainer(tenantId, ActionListener.wrap(r1 -> {listener.onResponse(new MLIndexInsightContainerDeleteResponse());}, listener::onFailure));
            }, listener::onFailure));

        }, listener::onFailure));
    }
}
