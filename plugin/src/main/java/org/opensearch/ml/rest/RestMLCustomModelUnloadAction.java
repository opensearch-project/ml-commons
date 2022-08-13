/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.custom.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.custom.unload.UnloadModelInput;
import org.opensearch.ml.common.transport.custom.unload.UnloadModelNodesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCustomModelUnloadAction extends BaseRestHandler {
    private static final String ML_UNLOAD_MODEL_ACTION = "ml_unload_model_action";
    private ClusterService clusterService;

    /**
     * Constructor
     */
    public RestMLCustomModelUnloadAction(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public String getName() {
        return ML_UNLOAD_MODEL_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/custom_model/unload", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        UnloadModelNodesRequest unloadModelNodesRequest = getRequest(request);
        return channel -> client.execute(MLUnloadModelAction.INSTANCE, unloadModelNodesRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    UnloadModelNodesRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        UnloadModelInput mlInput = UnloadModelInput.parse(parser);

        return new UnloadModelNodesRequest(getAllNodes(), mlInput);
    }

    private String[] getAllNodes() {
        Iterator<DiscoveryNode> iterator = clusterService.state().nodes().iterator();
        List<String> nodeIds = new ArrayList<>();
        while (iterator.hasNext()) {
            nodeIds.add(iterator.next().getId());
        }
        return nodeIds.toArray(new String[0]);
    }
}
