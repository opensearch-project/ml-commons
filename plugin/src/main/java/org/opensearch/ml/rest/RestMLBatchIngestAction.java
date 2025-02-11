/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionAction;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLBatchIngestAction extends BaseRestHandler {
    private static final String ML_BATCH_INGESTION_ACTION = "ml_batch_ingestion_action";

    @Override
    public String getName() {
        return ML_BATCH_INGESTION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_batch_ingestion", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLBatchIngestionRequest mlBatchIngestTaskRequest = getRequest(request);
        return channel -> client.execute(MLBatchIngestionAction.INSTANCE, mlBatchIngestTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLBatchIngestTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLBatchIngestTaskRequest
     */
    @VisibleForTesting
    MLBatchIngestionRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Batch Ingestion request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLBatchIngestionInput mlBatchIngestionInput = MLBatchIngestionInput.parse(parser);
        return new MLBatchIngestionRequest(mlBatchIngestionInput);
    }
}
