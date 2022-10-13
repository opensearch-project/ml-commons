/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;
import static org.opensearch.ml.utils.RestActionUtils.onFailure;
import static org.opensearch.ml.utils.RestActionUtils.splitCommaSeparatedParam;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;

import com.google.common.collect.ImmutableList;

@Log4j2
public class RestMLProfileAction extends BaseRestHandler {
    private static final String PROFILE_ML_ACTION = "profile_ml";

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
        log.info("Prepare request for {}", request.getHttpRequest());
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
                // node level profile
                List<MLProfileNodeResponse> nodeProfiles = r.getNodes().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
                log.info("Build MLProfileNodeResponse for size of {}", nodeProfiles.size());
                if (nodeProfiles.size() > 0) {
                    r.toXContent(builder, ToXContent.EMPTY_PARAMS);
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
