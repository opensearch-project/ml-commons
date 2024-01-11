/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;
import static org.opensearch.ml.utils.RestActionUtils.onFailure;
import static org.opensearch.ml.utils.RestActionUtils.splitCommaSeparatedParam;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileModelResponse;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLProfileAction extends BaseRestHandler {
    private static final String PROFILE_ML_ACTION = "profile_ml";

    private static final String VIEW = "view";
    private static final String MODEL_VIEW = "model";
    private static final String NODE_VIEW = "node";

    private ClusterService clusterService;

    /**
     * Constructor
     * @param clusterService cluster service
     */
    public RestMLProfileAction(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public String getName() {
        return PROFILE_ML_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List
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
        String view = RestActionUtils.getStringParam(request, VIEW).orElse(NODE_VIEW);
        String[] nodeIds = mlProfileInput.retrieveProfileOnAllNodes()
            ? getAllNodes(clusterService)
            : mlProfileInput.getNodeIds().toArray(new String[0]);
        MLProfileRequest mlProfileRequest = new MLProfileRequest(nodeIds, mlProfileInput);

        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            client.execute(MLProfileAction.INSTANCE, mlProfileRequest, ActionListener.wrap(r -> {
                builder.startObject();
                // node level profile
                List<MLProfileNodeResponse> nodeProfiles = r.getNodes().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
                log.debug("Build MLProfileNodeResponse for size of {}", nodeProfiles.size());
                if (nodeProfiles.size() > 0) {
                    if (NODE_VIEW.equals(view)) {
                        r.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    } else if (MODEL_VIEW.equals(view)) {
                        Map<String, MLProfileModelResponse> modelCentricProfileMap = buildModelCentricResult(nodeProfiles);
                        builder.startObject("models");
                        for (Map.Entry<String, MLProfileModelResponse> entry : modelCentricProfileMap.entrySet()) {
                            builder.field(entry.getKey(), entry.getValue());
                        }
                        builder.endObject();
                    }
                }
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            }, e -> {
                String errorMessage = "Failed to get ML node level profile";
                log.error(errorMessage, e);
                onFailure(channel, RestStatus.INTERNAL_SERVER_ERROR, errorMessage, e);
            }));
        };
    }

    /**
     * The data structure for node centric is:
     *  MLProfileNodeResponse:
     *      taskMap: Map<String, MLTask>
     *      modelMap: Map<String, MLModelProfile> model_id, MLModelProfile
     *  And we need to convert to format like this:
     *  modelMap: Map<String, Map<String, MLModelProfile>>
     */
    private Map<String, MLProfileModelResponse> buildModelCentricResult(List<MLProfileNodeResponse> nodeResponses) {
        // aggregate model information into one final map.
        Map<String, MLProfileModelResponse> modelCentricMap = new HashMap<>();
        for (MLProfileNodeResponse mlProfileNodeResponse : nodeResponses) {
            String nodeId = mlProfileNodeResponse.getNode().getId();
            Map<String, MLModelProfile> modelProfileMap = mlProfileNodeResponse.getMlNodeModels();
            Map<String, MLTask> taskProfileMap = mlProfileNodeResponse.getMlNodeTasks();
            for (Map.Entry<String, MLModelProfile> entry : modelProfileMap.entrySet()) {
                MLProfileModelResponse mlProfileModelResponse = modelCentricMap.get(entry.getKey());
                if (mlProfileModelResponse == null) {
                    mlProfileModelResponse = new MLProfileModelResponse(
                        entry.getValue().getTargetWorkerNodes(),
                        entry.getValue().getWorkerNodes()
                    );
                    modelCentricMap.put(entry.getKey(), mlProfileModelResponse);
                }
                if (mlProfileModelResponse.getTargetWorkerNodes() == null || mlProfileModelResponse.getWorkerNodes() == null) {
                    mlProfileModelResponse.setTargetWorkerNodes(entry.getValue().getTargetWorkerNodes());
                    mlProfileModelResponse.setWorkerNodes(entry.getValue().getWorkerNodes());
                }
                // Create a new object and remove targetWorkerNodes and workerNodes.
                MLModelProfile modelProfile = new MLModelProfile(
                    entry.getValue().getModelState(),
                    entry.getValue().getPredictor(),
                    null,
                    null,
                    entry.getValue().getModelInferenceStats(),
                    entry.getValue().getPredictRequestStats(),
                    entry.getValue().getMemSizeEstimationCPU(),
                    entry.getValue().getMemSizeEstimationGPU()
                );
                mlProfileModelResponse.getMlModelProfileMap().putAll(Map.of(nodeId, modelProfile));
            }

            for (Map.Entry<String, MLTask> entry : taskProfileMap.entrySet()) {
                String modelId = entry.getValue().getModelId();
                MLProfileModelResponse mlProfileModelResponse = modelCentricMap.get(modelId);
                if (mlProfileModelResponse == null) {
                    mlProfileModelResponse = new MLProfileModelResponse();
                    modelCentricMap.put(modelId, mlProfileModelResponse);
                }
                mlProfileModelResponse.getMlTaskMap().putAll(Map.of(entry.getKey(), entry.getValue()));
            }
        }
        return modelCentricMap;
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
        return mlProfileInput;
    }
}
