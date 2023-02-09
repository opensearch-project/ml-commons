/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.common.CommonValue.NODE_ID_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_MODEL_RELOAD_MAX_RETRY_TIMES;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.annotations.VisibleForTesting;

/**
 * Manager class for ML models and nodes. It contains ML model auto reload operations etc.
 */
@Log4j2
public class MLModelAutoReloader {

    private final Client client;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final DiscoveryNodeHelper nodeHelper;
    private final ThreadPool threadPool;
    private volatile Boolean enableAutoReloadModel;
    private volatile Integer autoReloadMaxRetryTimes;

    /**
     * constructor method， init all the params necessary for model auto reloading
     *
     * @param clusterService   clusterService
     * @param threadPool       threadPool
     * @param client           client
     * @param xContentRegistry xContentRegistry
     * @param nodeHelper       nodeHelper
     * @param settings         settings
     */
    public MLModelAutoReloader(
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeHelper,
        Settings settings
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeHelper = nodeHelper;
        this.threadPool = threadPool;

        enableAutoReloadModel = ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.get(settings);
        autoReloadMaxRetryTimes = ML_MODEL_RELOAD_MAX_RETRY_TIMES.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE, it -> enableAutoReloadModel = it);

        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_MODEL_RELOAD_MAX_RETRY_TIMES, it -> autoReloadMaxRetryTimes = it);
    }

    /**
     * the main method: model auto reloading
     */
    public void autoReloadModel() {
        log.info("auto reload model enabled: {} ", enableAutoReloadModel);

        // if we don't need to reload automatically, just return without doing anything
        if (!enableAutoReloadModel) {
            return;
        }

        // At opensearch startup, get local node id, if not ml node,we ignored, just return without doing anything
        if (!MLNodeUtils.isMLNode(clusterService.localNode())) {
            return;
        }

        String localNodeId = clusterService.localNode().getId();
        // auto reload all models of this local ml node
        threadPool.generic().submit(() -> {
            try {
                autoReloadModelByNodeId(localNodeId);
            } catch (ExecutionException | InterruptedException e) {
                log
                    .error(
                        "the model auto-reloading has exception,and the root cause message is: {}",
                        ExceptionUtils.getRootCauseMessage(e)
                    );
                throw new MLException(e);
            }
        });
    }

    /**
     * auto reload all the models under the node id<br> the node must be a ml node<br>
     *
     * @param localNodeId node id
     */
    @VisibleForTesting
    void autoReloadModelByNodeId(String localNodeId) throws ExecutionException, InterruptedException {
        StepListener<SearchResponse> queryTaskStep = new StepListener<>();
        StepListener<SearchResponse> getRetryTimesStep = new StepListener<>();
        StepListener<IndexResponse> saveLatestRetryTimesStep = new StepListener<>();

        if (!clusterService.state().metadata().indices().containsKey(ML_TASK_INDEX)) {
            // ML_TASK_INDEX did not exist,do nothing
            return;
        }

        queryTask(localNodeId, ActionListener.wrap(queryTaskStep::onResponse, queryTaskStep::onFailure));

        getRetryTimes(localNodeId, ActionListener.wrap(getRetryTimesStep::onResponse, getRetryTimesStep::onFailure));

        queryTaskStep.whenComplete(searchResponse -> {
            SearchHit[] hits = searchResponse.getHits().getHits();
            if (CollectionUtils.isEmpty(hits)) {
                return;
            }

            getRetryTimesStep.whenComplete(getReTryTimesResponse -> {
                int retryTimes = 0;
                // if getReTryTimesResponse is null,it means we get retryTimes at the first time,and the index
                // .plugins-ml-model-reload doesn't exist,so we should let retryTimes be zero(init value)
                // we don't do anything
                // if getReTryTimesResponse is not null,it means we have saved the value of retryTimes into the index
                // .plugins-ml-model-reload,so we get the value of the field MODEL_LOAD_RETRY_TIMES_FIELD
                if (getReTryTimesResponse != null) {
                    Map<String, Object> sourceAsMap = getReTryTimesResponse.getHits().getHits()[0].getSourceAsMap();
                    retryTimes = (Integer) sourceAsMap.get(MODEL_LOAD_RETRY_TIMES_FIELD);
                }

                // According to the node id to get retry times, if more than the max retry times, don't need to retry
                // that the number of unsuccessful reload has reached the maximum number of times, do not need to reload
                if (retryTimes > autoReloadMaxRetryTimes) {
                    log.info("have exceeded max retry times, always failure");
                    return;
                }

                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, hits[0].getSourceRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLTask mlTask = MLTask.parse(parser);

                    autoReloadModelByNodeAndModelId(localNodeId, mlTask.getModelId());

                    // if reload the model successfully,the number of unsuccessful reload should be reset to zero.
                    retryTimes = 0;
                } catch (MLException e) {
                    retryTimes++;
                    log.error("Can't auto reload model in node id {} ,has tried {} times\nThe reason is:{}", localNodeId, retryTimes, e);
                }

                // Store the latest value of the retryTimes and node id under the index ".plugins-ml-model-reload"
                saveLatestRetryTimes(
                    localNodeId,
                    retryTimes,
                    ActionListener.wrap(saveLatestRetryTimesStep::onResponse, saveLatestRetryTimesStep::onFailure)
                );
            }, getRetryTimesStep::onFailure);
        }, queryTaskStep::onFailure);

        saveLatestRetryTimesStep.whenComplete(response -> log.info("successfully complete all steps"), saveLatestRetryTimesStep::onFailure);
    }

    /**
     * auto reload 1 model under the node id
     *
     * @param localNodeId node id
     * @param modelId     model id
     */
    @VisibleForTesting
    void autoReloadModelByNodeAndModelId(String localNodeId, String modelId) throws MLException {
        String[] allNodeIds = nodeHelper.getAllNodeIds();
        List<String> allNodeIdList = new ArrayList<>(List.of(allNodeIds));
        if (!allNodeIdList.contains(localNodeId)) {
            allNodeIdList.add(localNodeId);
        }
        MLLoadModelRequest mlLoadModelRequest = new MLLoadModelRequest(modelId, allNodeIdList.toArray(new String[] {}), false, false);

        client
            .execute(
                MLLoadModelAction.INSTANCE,
                mlLoadModelRequest,
                ActionListener
                    .wrap(response -> log.info("the model {} is auto reloading under the node {} ", modelId, localNodeId), exception -> {
                        log.error("fail to reload model " + modelId + " under the node " + localNodeId + "\nthe reason is: " + exception);
                        throw new MLException(
                            "fail to reload model " + modelId + " under the node " + localNodeId + "\nthe reason is: " + exception
                        );
                    })
            );
    }

    /**
     * query task index, and get the result of "task_type"="LOAD_MODEL" and "state"="COMPLETED" and
     * "worker_node" match nodeId
     *
     * @param localNodeId one of query condition
     */
    @VisibleForTesting
    void queryTask(String localNodeId, ActionListener<SearchResponse> searchResponseActionListener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().from(0).size(1);

        QueryBuilder queryBuilder = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.matchPhraseQuery("task_type", "LOAD_MODEL"))
            .must(QueryBuilders.matchPhraseQuery("worker_node", localNodeId))
            .must(
                QueryBuilders
                    .boolQuery()
                    .should(QueryBuilders.matchPhraseQuery("state", "COMPLETED"))
                    .should(QueryBuilders.matchPhraseQuery("state", "COMPLETED_WITH_ERROR"))
            );
        searchSourceBuilder.query(queryBuilder);

        SortBuilder<FieldSortBuilder> sortBuilderOrder = new FieldSortBuilder("create_time").order(SortOrder.DESC);
        searchSourceBuilder.sort(sortBuilderOrder);

        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE)
            .setIndices(ML_TASK_INDEX)
            .setSource(searchSourceBuilder);

        searchRequestBuilder.execute(ActionListener.wrap(searchResponseActionListener::onResponse, exception -> {
            log.error("index {} not found, the reason is {}", ML_TASK_INDEX, exception);
            throw new IndexNotFoundException("index " + ML_TASK_INDEX + " not found");
        }));
    }

    /**
     * get retry times from the index ".plugins-ml-model-reload" by 1 ml node
     *
     * @param localNodeId the filter condition to query
     */
    @VisibleForTesting
    void getRetryTimes(String localNodeId, ActionListener<SearchResponse> searchResponseActionListener) {
        if (!clusterService.state().metadata().indices().containsKey(ML_MODEL_RELOAD_INDEX)) {
            // ML_MODEL_RELOAD_INDEX did not exist, it means it is our first time to do model auto-reloading operation
            searchResponseActionListener.onResponse(null);
        }

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(new String[] { MODEL_LOAD_RETRY_TIMES_FIELD }, null);
        QueryBuilder queryBuilder = QueryBuilders.idsQuery().addIds(localNodeId);
        searchSourceBuilder.query(queryBuilder);

        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE)
            .setIndices(ML_MODEL_RELOAD_INDEX)
            .setSource(searchSourceBuilder);

        searchRequestBuilder.execute(ActionListener.wrap(searchResponse -> {
            SearchHit[] hits = searchResponse.getHits().getHits();
            if (CollectionUtils.isEmpty(hits)) {
                searchResponseActionListener.onResponse(null);
                return;
            }

            searchResponseActionListener.onResponse(searchResponse);
        }, searchResponseActionListener::onFailure));
    }

    /**
     * save retry times
     * @param localNodeId node id
     * @param retryTimes actual retry times
     */
    @VisibleForTesting
    void saveLatestRetryTimes(String localNodeId, int retryTimes, ActionListener<IndexResponse> indexResponseActionListener) {
        Map<String, Object> content = new HashMap<>(2);
        content.put(NODE_ID_FIELD, localNodeId);
        content.put(MODEL_LOAD_RETRY_TIMES_FIELD, retryTimes);

        IndexRequestBuilder indexRequestBuilder = new IndexRequestBuilder(client, IndexAction.INSTANCE, ML_MODEL_RELOAD_INDEX)
            .setId(localNodeId)
            .setSource(content)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        indexRequestBuilder.execute(ActionListener.wrap(indexResponse -> {
            if (indexResponse.status() == RestStatus.CREATED || indexResponse.status() == RestStatus.OK) {
                log.info("node id:{} insert retry times successfully", localNodeId);
                indexResponseActionListener.onResponse(indexResponse);
                return;
            }
            indexResponseActionListener.onFailure(new MLException("node id:" + localNodeId + " insert retry times unsuccessfully"));
        }, indexResponseActionListener::onFailure));
    }
}
