package org.opensearch.ml.action.IndexInsight;

import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.index.Index;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private Client client;
    private NamedXContentRegistry xContentRegistry;

    @Inject
    public GetIndexInsightTransportAction(TransportService transportService, ActionFilters actionFilters, NamedXContentRegistry xContentRegistry, Client client){
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        String indexName = mlIndexInsightGetRequest.getIndexName();
        SearchRequest searchRequest = new SearchRequest(ML_INDEX_INSIGHT_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new TermQueryBuilder(INDEX_NAME_FIELD, indexName));
        searchRequest.source(searchSourceBuilder);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLIndexInsightGetResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                        SearchHit[] hits = searchResponse.getHits().getHits();
                        SearchHit hit = hits[0];
                        try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hit.getSourceRef())) {
                            IndexInsight indexInsight = IndexInsight.parse(parser);
                            wrappedListener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(indexInsight).build());
                        } catch (Exception e) {
                            wrappedListener.onFailure(e);
                        }
                    },
                    wrappedListener::onFailure));

        } catch (Exception e){
            log.error("fail to get index insight", e);
            actionListener.onFailure(e);
        }
    }
}
