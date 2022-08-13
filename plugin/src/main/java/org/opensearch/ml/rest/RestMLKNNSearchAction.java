/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseListener;
import org.opensearch.client.RestClient;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.execute.sentence_transformer.SentenceTransformerInput;
import org.opensearch.ml.common.output.execute.sentence_transformer.SentenceTransformerOutput;
import org.opensearch.ml.common.search.MLKNNSearchInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@Log4j2
public class RestMLKNNSearchAction extends BaseRestHandler {
    private static final String ML_KNN_SEARCH_ACTION = "ml_knn_search_action";
    private final NamedXContentRegistry xContentRegistry;
    private RestClient restClient;

    /**
     * Constructor
     * @param xContentRegistry
     */
    public RestMLKNNSearchAction(NamedXContentRegistry xContentRegistry) {
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public String getName() {
        return ML_KNN_SEARCH_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_search", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (restClient == null) {
            restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
        }
        MLKNNSearchInput input = getRequest(request);
        String queryTemplate = "{  \"size\": TOP_K,\n"
            + "  \"query\": {\n"
            + "    \"knn\": {\n"
            + "      \"VECTOR_FIELD\": {\n"
            + "        \"vector\": [ VECTOR ],\n"
            + "        \"k\": TOP_K \n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"_source\": [ \"SOURCE_FIELD\" ] }";
        return channel -> {
            SentenceTransformerInput stInput = new SentenceTransformerInput(ImmutableList.of(input.getQuery()));
            MLExecuteTaskRequest mlExecuteTaskRequest = new MLExecuteTaskRequest(FunctionName.SENTENCE_TRANSFORMER, stInput);
            client.execute(MLExecuteTaskAction.INSTANCE, mlExecuteTaskRequest, ActionListener.wrap(res -> {
                SentenceTransformerOutput output = (SentenceTransformerOutput) res.getOutput();
                float[] vectorValues = output.getResult().get(0);
                String vectorString = StringUtils.join(ArrayUtils.toObject(vectorValues), ",");
                String sourceFields = String.join("\",\"", input.getSourceFields());
                String topK = input.getK() + "";
                String vectorField = input.getVectorField();
                String knnQuery = queryTemplate
                    .replaceAll("VECTOR_FIELD", vectorField)
                    .replaceAll("TOP_K", topK)
                    .replaceAll("VECTOR", vectorString)
                    .replaceAll("SOURCE_FIELD", sourceFields);
                Request searchRequest = new Request("GET", "/" + input.getIndex() + "/_search");
                searchRequest.setJsonEntity(knnQuery);
                XContentBuilder xContentBuilder = channel.newBuilder();
                restClient.performRequestAsync(searchRequest, new ResponseListener() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            String entityString = EntityUtils.toString(response.getEntity());
                            SearchResponse searchResponse = SearchResponse.fromXContent(parser(entityString, false));
                            XContentBuilder searchResponseBuilder = searchResponse.toXContent(xContentBuilder, EMPTY_PARAMS);
                            BytesRestResponse bytesRestResponse = new BytesRestResponse(RestStatus.OK, searchResponseBuilder);
                            channel.sendResponse(bytesRestResponse);
                        } catch (IOException exception) {
                            log.error("Failed to parse KNN search response", exception);
                            RestMLKNNSearchAction.onFailure(channel, exception);
                        }
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        log.error("Failed to search KNN index", exception);
                        RestMLKNNSearchAction.onFailure(channel, exception);
                    }
                });
            }, ex -> {
                log.error("Failed to search", ex);
                RestMLKNNSearchAction.onFailure(channel, ex);
            }));
        };
    }

    public static void onFailure(RestChannel channel, Exception exception) {
        try {
            XContentBuilder errorBuilder = channel.newErrorBuilder();
            errorBuilder.startObject();
            errorBuilder.field("error", ExceptionUtils.getStackTrace(exception));
            errorBuilder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, errorBuilder));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private XContentParser parser(String xc, boolean skipFirstToken) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, xc);
        if (skipFirstToken) {
            parser.nextToken();
        }
        return parser;
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLKNNSearchInput getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLKNNSearchInput input = MLKNNSearchInput.parse(parser);
        return input;
    }
}
