/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * This is an extension of SearchResponse that adds LLM-generated answers to search responses in a dedicated "ext" section.
 *
 * TODO Add ExtBuilders to SearchResponse and get rid of this class.
 */
public class GenerativeSearchResponse extends SearchResponse {

    private final String answer;

    public GenerativeSearchResponse(
        String answer,
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
        this.answer = answer;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        innerToXContent(builder, params);
        /* start of ext */ builder.startObject("ext");
        /*   start of our stuff */ builder.startObject("generative_qa");
        /*     body of our stuff    */ builder.field("answer", this.answer);
        /*   end of our stuff   */ builder.endObject();
        /* end of ext */ builder.endObject();
        builder.endObject();
        return builder;
    }
}
