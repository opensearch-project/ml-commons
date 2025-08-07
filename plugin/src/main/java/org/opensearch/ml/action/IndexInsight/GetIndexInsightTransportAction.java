package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightAccessControllerHelper;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.indexInsight.StatisticalDataTask;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexDescriptionTask;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;
import org.opensearch.ml.engine.indices.MLIndicesHandler;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private Client client;
    private NamedXContentRegistry xContentRegistry;
    private MLIndicesHandler mlIndicesHandler;
    private ClusterService clusterService;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        Client client,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        String indexName = mlIndexInsightGetRequest.getIndexName();
        
        // Initialize index insight index if absent
        mlIndicesHandler.initMLIndexInsightIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                actionListener.onFailure(new Exception("Failed to create index insight index"));
                return;
            }
            
            ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
            SearchRequest searchRequest = new SearchRequest(ML_INDEX_INSIGHT_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must(new TermQueryBuilder(INDEX_NAME_FIELD, indexName));
            boolQueryBuilder.must(new TermQueryBuilder(TASK_TYPE_FIELD, mlIndexInsightGetRequest.getTargetIndexInsight().toString()));
            searchSourceBuilder.query(boolQueryBuilder);
            searchRequest.source(searchSourceBuilder);

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<MLIndexInsightGetResponse> wrappedListener = ActionListener
                    .runBefore(actionListener, () -> context.restore());
                client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                    SearchHit[] hits = searchResponse.getHits().getHits();
                    
                    if (hits.length == 0) {
                        // No record found - execute task
                        executeTaskAndReturn(mlIndexInsightGetRequest, wrappedListener);
                        return;
                    }
                    
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hits[0].getSourceRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        IndexInsight existingInsight = IndexInsight.parse(parser);
                        handleExistingRecord(existingInsight, mlIndexInsightGetRequest, wrappedListener);
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }, wrappedListener::onFailure));

            } catch (Exception e) {
                log.error("fail to get index insight", e);
                actionListener.onFailure(e);
            }
            }, actionListener::onFailure);
            IndexInsightAccessControllerHelper.verifyAccessController(client, actionAfterDryRun, indexName);
        }, actionListener::onFailure));
    }
    
    private void executeTaskAndReturn(MLIndexInsightGetRequest request, ActionListener<MLIndexInsightGetResponse> listener) {
        IndexInsightTask task = createTask(request);
        task.execute(ActionListener.wrap(
            insight -> {
                // Task completed, return result directly
                listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(insight).build());
            },
            listener::onFailure
        ));
    }
    
    private void handleExistingRecord(IndexInsight existingInsight, MLIndexInsightGetRequest request, 
                                     ActionListener<MLIndexInsightGetResponse> listener) {
        IndexInsightTaskStatus status = existingInsight.getStatus();
        long lastUpdateTime = existingInsight.getLastUpdatedTime().toEpochMilli();
        long currentTime = Instant.now().toEpochMilli();
        String indexName = request.getIndexName();
        
        switch (status) {
            case COMPLETED:
                if ((currentTime - lastUpdateTime) <= IndexInsightTask.UPDATE_INTERVAL) {
                    // Not expired - return immediately
                    listener.onResponse(MLIndexInsightGetResponse.builder()
                        .indexInsight(existingInsight).build());
                    return;
                }
                // Expired - re-execute task
                executeTaskAndReturn(request, listener);
                break;
                
            case GENERATING:
                if ((currentTime - lastUpdateTime) <= IndexInsightTask.GENERATING_TIMEOUT) {
                    // Not timeout - return generating message
                    log.info("Index insight for index {} with task type {} is being generated, please wait...", 
                            indexName, request.getTargetIndexInsight());
                    listener.onFailure(new OpenSearchStatusException(
                        "Index insight is being generated, please wait...", RestStatus.ACCEPTED));
                    return;
                }
                // Timeout - re-execute task
                executeTaskAndReturn(request, listener);
                break;
                
            case FAILED:
                // Failed - retry task
                executeTaskAndReturn(request, listener);
                break;
        }
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
            default:
                throw new IllegalArgumentException("Unsupported task type: " + request.getTargetIndexInsight());
        }
    }
    
    private void queryAndReturnResult(MLIndexInsightGetRequest request, ActionListener<MLIndexInsightGetResponse> listener) {
        SearchRequest searchRequest = new SearchRequest(ML_INDEX_INSIGHT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(new TermQueryBuilder(INDEX_NAME_FIELD, request.getIndexName()));
        boolQueryBuilder.must(new TermQueryBuilder(TASK_TYPE_FIELD, request.getTargetIndexInsight().toString()));
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);
        
        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            SearchHit[] hits = searchResponse.getHits().getHits();
            if (hits.length > 0) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hits[0].getSourceRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    IndexInsight insight = IndexInsight.parse(parser);
                    listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(insight).build());
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new Exception("Task completed but result not found"));
            }
        }, listener::onFailure));
    }
}
