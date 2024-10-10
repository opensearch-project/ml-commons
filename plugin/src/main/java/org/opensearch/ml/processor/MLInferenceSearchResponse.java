/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLInferenceSearchResponse extends SearchResponse {
    private static final String EXT_SECTION_NAME = "ext";

    private Map<String, Object> params;

    public MLInferenceSearchResponse(
        Map<String, Object> params,
        SearchResponseSections internalResponse,
        String scrollId,
        int totalShards,
        int successfulShards,
        int skippedShards,
        long tookInMillis,
        ShardSearchFailure[] shardFailures,
        Clusters clusters
    ) {
        super(internalResponse, scrollId, totalShards, successfulShards, skippedShards, tookInMillis, shardFailures, clusters);
        this.params = params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getParams() {
        return this.params;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        innerToXContent(builder, params);

        if (this.params != null) {
            builder.startObject(EXT_SECTION_NAME);
            builder.field(MLInferenceSearchResponseProcessor.TYPE, this.params);

            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
