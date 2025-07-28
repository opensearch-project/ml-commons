package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

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
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private Client client;
    private NamedXContentRegistry xContentRegistry;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        Client client
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        String indexName = mlIndexInsightGetRequest.getIndexName();
        ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
            SearchRequest searchRequest = new SearchRequest(ML_INDEX_INSIGHT_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must(new TermQueryBuilder(INDEX_NAME_FIELD, indexName));
            if (mlIndexInsightGetRequest.getTargetIndexInsight() != MLIndexInsightType.ALL) {
                boolQueryBuilder.must(new TermQueryBuilder(TASK_TYPE_FIELD, mlIndexInsightGetRequest.getTargetIndexInsight().toString()));
            }
            searchSourceBuilder.query(boolQueryBuilder);
            searchRequest.source(searchSourceBuilder);

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<MLIndexInsightGetResponse> wrappedListener = ActionListener
                    .runBefore(actionListener, () -> context.restore());
                client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                    SearchHit[] hits = searchResponse.getHits().getHits();
                    if (hits.length == 0) {
                        wrappedListener
                            .onFailure(
                                new OpenSearchStatusException("The index insight hasn't created, will create now.", RestStatus.FORBIDDEN)
                            );
                        return;
                    }
                    List<IndexInsight> indexInsights = new ArrayList<>();
                    for (SearchHit hit : hits) {
                        try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hit.getSourceRef())) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            IndexInsight indexInsight = IndexInsight.parse(parser);
                            indexInsights.add(indexInsight);
                        } catch (Exception e) {
                            wrappedListener.onFailure(e);
                        }
                    }
                    wrappedListener.onResponse(MLIndexInsightGetResponse.builder().indexInsights(indexInsights).build());
                }, wrappedListener::onFailure));

            } catch (Exception e) {
                log.error("fail to get index insight", e);
                actionListener.onFailure(e);
            }
        }, actionListener::onFailure);
        IndexInsightAccessControllerHelper.verifyAccessController(client, actionAfterDryRun, indexName);
    }
}
