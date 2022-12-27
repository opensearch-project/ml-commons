/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.*;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.get.MultiGetItemResponse;
import org.opensearch.action.get.MultiGetRequestBuilder;
import org.opensearch.action.get.MultiGetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.factory.MultiGetRequestBuilderFactory;
import org.opensearch.ml.profile.MLDeploymentProfile;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;

import com.google.common.collect.ImmutableList;

@Log4j2
public class RestMLProfileAction extends BaseRestHandler {
    private static final String PROFILE_ML_ACTION = "profile_ml";

    private static final String PROFILE_AND_DEPLOYMENT_FIELD = "profile_and_deployment";

    private static final String PROFILE_ONLY_FIELD = "profile";
    private static final String DEPLOYMENT_ONLY_FIELD = "deployment";
    private static final String ALL_FIELD = "all";

    private static final List<String> VALID_PROFILE_AND_DEPLOYMENT_VALUES = Arrays
        .asList(PROFILE_ONLY_FIELD, DEPLOYMENT_ONLY_FIELD, ALL_FIELD);

    private ClusterService clusterService;

    private MultiGetRequestBuilderFactory multiGetRequestBuilderFactory;

    /**
     * Constructor
     * @param clusterService cluster service
     */
    public RestMLProfileAction(ClusterService clusterService, MultiGetRequestBuilderFactory multiGetRequestBuilderFactory) {
        this.clusterService = clusterService;
        this.multiGetRequestBuilderFactory = multiGetRequestBuilderFactory;
    }

    @Override
    public String getName() {
        return PROFILE_ML_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/profile/models/{model_id}"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/profile/models"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/profile/tasks/{task_id}"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/profile/tasks"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/profile")
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        boolean hasContent = request.hasContent();
        MLProfileInput mlProfileInput;
        if (hasContent) {
            XContentParser parser = request.contentParser();
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            mlProfileInput = MLProfileInput.parse(parser);
        } else {
            mlProfileInput = createMLProfileInputFromRequestParams(request);
        }
        String[] nodeIds = mlProfileInput.retrieveProfileOnAllNodes()
            ? getAllNodes(clusterService)
            : mlProfileInput.getNodeIds().toArray(new String[0]);
        MLProfileRequest mlProfileRequest = new MLProfileRequest(nodeIds, mlProfileInput);

        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            client.execute(MLProfileAction.INSTANCE, mlProfileRequest, ActionListener.wrap(r -> {
                builder.startObject();
                List<MLProfileNodeResponse> nodeProfiles = r.getNodes().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
                if (nodeProfiles.size() > 0) {
                    log.debug("Build MLProfileNodeResponse for size of {}", nodeProfiles.size());
                    if (ALL_FIELD.equals(mlProfileInput.getProfileAndDeployment())) {
                        r.toXContent(builder, ToXContent.EMPTY_PARAMS);
                        buildModelDeploymentData(nodeProfiles, client, builder, channel);
                    } else if (DEPLOYMENT_ONLY_FIELD.equals(mlProfileInput.getProfileAndDeployment())) {
                        buildModelDeploymentData(nodeProfiles, client, builder, channel);
                    } else {
                        // Return default profile data.
                        // node level profile
                        r.toXContent(builder, ToXContent.EMPTY_PARAMS);
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    }
                }
            }, e -> {
                String errorMessage = "Failed to get ML node level profile";
                log.error(errorMessage, e);
                onFailure(channel, RestStatus.INTERNAL_SERVER_ERROR, errorMessage, e);
            }));
        };
    }

    private void sendDefaultProfileResponseWhenExceptionOccurred(XContentBuilder builder, RestChannel channel) {
        try {
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        } catch (IOException e) {
            log.error("IO exception occurred when building profile response!", e);
            onFailure(channel, RestStatus.INTERNAL_SERVER_ERROR, "Failed to get ML node level profile", e);
        }
    }

    private void buildModelDeploymentData(
        List<MLProfileNodeResponse> nodeProfiles,
        NodeClient client,
        XContentBuilder builder,
        RestChannel channel
    ) {
        Map<String, String[]> modelIdDeployments = nodeProfiles
            .stream()
            .map(MLProfileNodeResponse::getMlNodeModels)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .filter(x -> x.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, x -> x.getValue().getWorkerNodes(), (k1, k2) -> k1));

        final List<MLDeploymentProfile> mlDeploymentProfiles = new ArrayList<>();
        ActionListener<MultiGetResponse> mlModelIndexResponseListener = ActionListener.wrap(r1 -> {
            MultiGetItemResponse[] multiGetItemResponses = r1.getResponses();
            Arrays.stream(multiGetItemResponses).map(MultiGetItemResponse::getResponse).filter(Objects::nonNull).forEach(x -> {
                Map<String, Object> map = x.getSourceAsMap();
                if (map != null && map.size() > 0) {
                    String name = (String) map.get("name");
                    MLDeploymentProfile mlDeploymentProfile = new MLDeploymentProfile(name, x.getId());
                    mlDeploymentProfiles.add(mlDeploymentProfile);
                }
            });
            if (mlDeploymentProfiles.isEmpty()) {
                log.error("Queried ml model index data state incorrect, returning profile data only!");
                sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
                return;
            }
            Map<String, String> modelExpectedNodesMap = new HashMap<>();
            ActionListener<SearchResponse> mlTaskIndexResponseListener = ActionListener.wrap(r2 -> {
                SearchHits hits = r2.getHits();
                if (hits == null || hits.getHits() == null) {
                    log.error("Failed to find ml task index data, returning profile data only!");
                    sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
                    return;
                }
                SearchHit[] searchHits = hits.getHits();
                Arrays.stream(searchHits).filter(Objects::nonNull).map(x -> {
                    try (
                        XContentParser parser = MLNodeUtils.createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, x.getSourceRef())
                    ) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        return MLTask.parse(parser);
                    } catch (Exception e) {
                        log.error("Deserializing ml task index data encountered exception, returning profile data only!", e);
                        return null;
                    }
                })
                    .filter(Objects::nonNull)
                    .filter(x -> MLTaskType.LOAD_MODEL == x.getTaskType())
                    .filter(x -> x.getCreateTime() != null)
                    .filter(x -> x.getWorkerNode() != null)
                    .forEach(x -> {
                        String modelId = x.getModelId();
                        String workNode = x.getWorkerNode();
                        modelExpectedNodesMap.putIfAbsent(modelId, workNode);
                    });
                if (modelExpectedNodesMap.isEmpty()) {
                    log.error("Failed to build expected deployment nodes map, returning profile data only!");
                    sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
                    return;
                }
                Map<String, List<String>> notDeploymentNodes = new HashMap<>();
                for (Map.Entry<String, String[]> entry : modelIdDeployments.entrySet()) {
                    String modelId = entry.getKey();
                    Set<String> workerNode = Arrays.stream(entry.getValue()).collect(Collectors.toSet());
                    String expectedWorkerNodes = modelExpectedNodesMap.get(modelId);
                    if (Strings.isNullOrEmpty(expectedWorkerNodes))
                        continue;
                    List<String> notDeployedNodes = Arrays
                        .stream(expectedWorkerNodes.split(","))
                        .filter(x -> !Strings.isNullOrEmpty(x))
                        .filter(x -> !workerNode.contains(x))
                        .collect(Collectors.toList());
                    notDeploymentNodes.put(modelId, notDeployedNodes);
                }

                Map<String, MLDeploymentProfile> deploymentStatus = mlDeploymentProfiles.stream().peek(x -> {
                    x.setTargetNodeIds(Arrays.asList(modelExpectedNodesMap.get(x.getModelId()).split(",")));
                    x.setNotDeployedNodeIds(notDeploymentNodes.get(x.getModelId()));
                }).collect(Collectors.toMap(MLDeploymentProfile::getModelId, Function.identity()));
                builder.startObject("models");
                for (Map.Entry<String, MLDeploymentProfile> entry : deploymentStatus.entrySet()) {
                    builder.field(entry.getKey(), entry.getValue());
                }
                builder.endObject();
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            }, e -> {
                log.error("Exception occurred when fetching ml task index data, returning profile data only!", e);
                sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
            });

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                TermsQueryBuilder termsQueryBuilder = QueryBuilders.termsQuery(MLTask.MODEL_ID_FIELD, modelIdDeployments.keySet());
                TermsQueryBuilder stateQueryBuilder = QueryBuilders.termsQuery(MLTask.STATE_FIELD, MLTaskState.COMPLETED.name());
                BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().should(termsQueryBuilder).should(stateQueryBuilder);
                FieldSortBuilder sortBuilder = SortBuilders.fieldSort(MLTask.CREATE_TIME_FIELD).order(SortOrder.DESC);
                client
                    .prepareSearch(ML_TASK_INDEX)
                    .setQuery(boolQueryBuilder)
                    .addSort(sortBuilder)
                    .setSize(1)
                    .execute(mlTaskIndexResponseListener);
            } catch (Exception e) {
                log.error("Failed to find model deployment tasks, returning profile data only!", e);
                sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
            }
        }, e -> {
            log.error("Exception occurred when fetching ml model index data, returning profile data only!", e);
            sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
        });

        String[] includes = new String[] { MLModel.MODEL_NAME_FIELD };
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        FetchSourceContext fetchContext = new FetchSourceContext(true, includes, excludes);
        MultiGetRequestBuilder multiGetRequestBuilder = multiGetRequestBuilderFactory.createMultiGetRequestBuilder(client);
        try {
            XContentBuilder xContentBuilder = XContentFactory.jsonBuilder().startObject().startArray("ids");
            for (String modelId : modelIdDeployments.keySet()) {
                xContentBuilder.value(modelId);
            }
            xContentBuilder.endArray().endObject();
            XContentParser contentParser = createParser(
                NamedXContentRegistry.EMPTY,
                xContentBuilder.contentType().xContent(),
                BytesReference.bytes(xContentBuilder)
            );
            multiGetRequestBuilder.request().add(ML_MODEL_INDEX, null, fetchContext, null, contentParser, true);
        } catch (Exception e) {
            log.error("Failed to build model index query request, returning profile data only!", e);
            sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            multiGetRequestBuilder.execute(mlModelIndexResponseListener);
        } catch (Exception e) {
            log.error("Failed to find ml models index data, returning profile data only!", e);
            sendDefaultProfileResponseWhenExceptionOccurred(builder, channel);
        }
    }

    protected static final XContentParser createParser(NamedXContentRegistry namedXContentRegistry, XContent xContent, BytesReference data)
        throws IOException {
        if (data instanceof BytesArray) {
            BytesArray array = (BytesArray) data;
            return xContent
                .createParser(namedXContentRegistry, LoggingDeprecationHandler.INSTANCE, array.array(), array.offset(), array.length());
        } else {
            return xContent.createParser(namedXContentRegistry, LoggingDeprecationHandler.INSTANCE, data.streamInput());
        }
    }

    MLProfileInput createMLProfileInputFromRequestParams(RestRequest request) {
        MLProfileInput mlProfileInput = new MLProfileInput();
        Optional<String[]> modelIds = splitCommaSeparatedParam(request, PARAMETER_MODEL_ID);
        String uri = request.getHttpRequest().uri();
        boolean profileModel = uri.contains("models");
        boolean profileTask = uri.contains("tasks");
        if (modelIds.isPresent()) {
            mlProfileInput.getModelIds().addAll(Arrays.asList(modelIds.get()));
        } else if (profileModel) { // For this case, the URI will be /_plugins/_ml/profile/models
            mlProfileInput.setReturnAllModels(true);
        }
        Optional<String[]> taskIds = splitCommaSeparatedParam(request, PARAMETER_TASK_ID);
        if (taskIds.isPresent()) {
            mlProfileInput.getTaskIds().addAll(Arrays.asList(taskIds.get()));
        } else if (profileTask) { // For this case, the URI will be /_plugins/_ml/profile/tasks
            mlProfileInput.setReturnAllTasks(true);
        }
        if (!profileModel && !profileTask) { // For this case, the URI will be /_plugins/_ml/profile
            mlProfileInput.setReturnAllTasks(true);
            mlProfileInput.setReturnAllModels(true);
        }
        String profileAndDeployment = RestActionUtils
            .getStringParam(request, PROFILE_AND_DEPLOYMENT_FIELD)
            .filter(VALID_PROFILE_AND_DEPLOYMENT_VALUES::contains)
            .orElse(PROFILE_ONLY_FIELD);
        mlProfileInput.setProfileAndDeployment(profileAndDeployment);
        return mlProfileInput;
    }
}
