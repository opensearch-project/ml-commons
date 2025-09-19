/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * This is an extension of SearchResponse that adds LLM-generated answers to search responses in a dedicated "ext" section.
 *
 * TODO Add ExtBuilders to SearchResponse and get rid of this class.
 */
public class GenerativeSearchResponse extends SearchResponse {

    private static final String EXT_SECTION_NAME = "ext";
    private static final String GENERATIVE_QA_ANSWER_FIELD_NAME = "answer";
    private static final String GENERATIVE_QA_ERROR_FIELD_NAME = "error";
    private static final String INTERACTION_ID_FIELD_NAME = "message_id";

    private final String answer;
    private String errorMessage;
    private final String interactionId;

    public GenerativeSearchResponse(
        String answer,
        String errorMessage,
        SearchResponseSections internalResponse,
        String scrollId,
        int totalShards,
        int successfulShards,
        int skippedShards,
        long tookInMillis,
        ShardSearchFailure[] shardFailures,
        Clusters clusters,
        String interactionId
    ) {
        super(internalResponse, scrollId, totalShards, successfulShards, skippedShards, tookInMillis, shardFailures, clusters);
        this.answer = answer;
        if (answer == null) {
            this.errorMessage = Objects.requireNonNull(errorMessage, "If answer is not given, errorMessage must be provided.");
        }
        this.interactionId = interactionId;
    }

    @Override
    public XContentBuilder innerToXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        super.innerToXContent(builder, params);
        /* start of ext */ builder.startObject(EXT_SECTION_NAME);
        /*   start of our stuff */ builder.startObject(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE);
        if (answer == null) {
            builder.field(GENERATIVE_QA_ERROR_FIELD_NAME, this.errorMessage);
        } else {
            /*     body of our stuff    */
            builder.field(GENERATIVE_QA_ANSWER_FIELD_NAME, this.answer);
            if (this.interactionId != null) {
                /*     interaction id       */
                builder.field(INTERACTION_ID_FIELD_NAME, this.interactionId);
            }
        }
        /*   end of our stuff   */ builder.endObject();
        /* end of ext */ builder.endObject();
        return builder;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        innerToXContent(builder, params);
        builder.endObject();
        return builder;
    }
}
