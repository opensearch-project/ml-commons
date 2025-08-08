package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.FIXED_INDEX_INSIGHT_CONTAINER_ID;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONTAINER_INDEX;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexInsightAccessControllerHelper;
import org.opensearch.ml.common.indexInsight.IndexInsightContainer;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.LogRelatedIndexCheckTask;
import org.opensearch.ml.common.indexInsight.StatisticalDataTask;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private Client client;
    private SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLIndicesHandler mlIndicesHandler;
    private ClusterService clusterService;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.sdkClient = sdkClient;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        String indexName = mlIndexInsightGetRequest.getIndexName();
        String tenantId = mlIndexInsightGetRequest.getTenantId();

        ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
                try (ThreadContext.StoredContext getContext = client.threadPool().getThreadContext().stashContext()) {
                    sdkClient
                        .getDataObjectAsync(
                            GetDataObjectRequest
                                .builder()
                                .tenantId(tenantId)
                                .index(ML_INDEX_INSIGHT_CONTAINER_INDEX)
                                .id(FIXED_INDEX_INSIGHT_CONTAINER_ID)
                                .build()
                        )
                        .whenComplete((r1, throwable) -> {
                            getContext.restore();
                            if (throwable != null) {
                                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                                log.error("Failed to index index insight container", cause);
                                actionListener.onFailure(cause);
                            } else {
                                GetResponse getResponse = r1.getResponse();
                                if (getResponse.isExists()) {
                                    try (
                                        XContentParser parser = jsonXContent
                                            .createParser(
                                                xContentRegistry,
                                                LoggingDeprecationHandler.INSTANCE,
                                                getResponse.getSourceAsString()
                                            )
                                    ) {
                                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                        IndexInsightContainer indexInsightContainer = IndexInsightContainer.parse(parser);
                                        String targetIndex = indexInsightContainer.getIndexName();
                                        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                                            ActionListener<MLIndexInsightGetResponse> wrappedListener = ActionListener
                                                .runBefore(actionListener, () -> context.restore());
                                            executeTaskAndReturn(mlIndexInsightGetRequest, targetIndex, tenantId, wrappedListener);
                                        } catch (Exception e) {
                                            log.error("fail to get index insight", e);
                                            actionListener.onFailure(e);
                                        }
                                    } catch (Exception e) {
                                        actionListener.onFailure(e);
                                    }
                                } else {
                                    actionListener.onFailure(new RuntimeException("The container is not set yet"));
                                }
                            }
                        });
                } catch (Exception e) {
                    actionListener.onFailure(e);
                }
        }, actionListener::onFailure);
        IndexInsightAccessControllerHelper.verifyAccessController(client, actionAfterDryRun, indexName);
    }

    private void executeTaskAndReturn(
        MLIndexInsightGetRequest request,
        String targetIndex,
        String tenantId,
        ActionListener<MLIndexInsightGetResponse> listener
    ) {
        IndexInsightTask task = createTask(request);
        task.execute(targetIndex, tenantId, ActionListener.wrap(insight -> {
            // Task completed, return result directly
            listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(insight).build());
        }, listener::onFailure));
    }

    private IndexInsightTask createTask(MLIndexInsightGetRequest request) {
        switch (request.getTargetIndexInsight()) {
            case STATISTICAL_DATA:
                try {
                    return new StatisticalDataTask(request.getIndexName(), client);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to create statistical data task for index: " + request.getIndexName(), e);
                }
            case FIELD_DESCRIPTION:
                // Need to get mapping metadata for field description task
                try {
                    String indexName = request.getIndexName();
                    MappingMetadata mappingMetadata = clusterService.state().metadata().index(indexName).mapping();
                    return new FieldDescriptionTask(indexName, mappingMetadata, client, clusterService);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to get mapping for index: " + request.getIndexName(), e);
                }
            case INDEX_DESCRIPTION:
                // Need to get mapping metadata for index description task
                try {
                    String indexName = request.getIndexName();
                    MappingMetadata mappingMetadata = clusterService.state().metadata().index(indexName).mapping();
                    return new IndexDescriptionTask(indexName, mappingMetadata, client, clusterService);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to get mapping for index: " + request.getIndexName(), e);
                }
            case LOG_RELATED_INDEX_CHECK:
                try {
                    return new LogRelatedIndexCheckTask(request.getIndexName(), client, clusterService);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Failed to create log related index check task for index: " + request.getIndexName(),
                        e
                    );
                }
            default:
                throw new IllegalArgumentException("Unsupported task type: " + request.getTargetIndexInsight());
        }
    }
}
