/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchExtBuilder;
import org.opensearch.search.profile.SearchProfileShardResults;

public class MLInferenceSearchResponse extends SearchResponse {

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

    /**
     * The sections of this response, with the ml_inference output as one of their ext entries.
     * Other response processors, such as agentic_context, rebuild the response from this list of
     * extensions, so the output has to be in it for them to keep it. The ext section is written once
     * from that list, so the output also does not become a second ext object next to theirs.
     */
    @Override
    public SearchResponseSections getInternalResponse() {
        SearchResponseSections sections = super.getInternalResponse();
        if (this.params == null) {
            return sections;
        }
        Map<String, Object> output = new HashMap<>();
        List<SearchExtBuilder> extensions = new ArrayList<>();
        for (SearchExtBuilder extension : sections.getSearchExtBuilders()) {
            if (extension instanceof MLInferenceExtBuilder) {
                // output of an earlier ml_inference processor in the same pipeline
                output.putAll(((MLInferenceExtBuilder) extension).params);
            } else {
                extensions.add(extension);
            }
        }
        output.putAll(this.params);
        extensions.add(new MLInferenceExtBuilder(output));
        return new SearchResponseSections(
            sections.hits(),
            sections.aggregations(),
            sections.suggest(),
            sections.timedOut(),
            sections.terminatedEarly(),
            sections.profile().isEmpty() ? null : new SearchProfileShardResults(sections.profile()),
            sections.getNumReducePhases(),
            extensions,
            sections.getProcessorResult()
        );
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (this.params == null) {
            return super.toXContent(builder, params);
        }
        return new SearchResponse(
            getInternalResponse(),
            getScrollId(),
            getTotalShards(),
            getSuccessfulShards(),
            getSkippedShards(),
            getTook().millis(),
            getShardFailures(),
            getClusters()
        ).toXContent(builder, params);
    }

    /**
     * The ml_inference entry of the ext section. It is only rendered, never sent between nodes.
     */
    static final class MLInferenceExtBuilder extends SearchExtBuilder {
        private final Map<String, Object> params;

        MLInferenceExtBuilder(Map<String, Object> params) {
            this.params = params;
        }

        @Override
        public String getWriteableName() {
            return MLInferenceSearchResponseProcessor.TYPE;
        }

        @Override
        public void writeTo(StreamOutput out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params ignored) throws IOException {
            return builder.field(MLInferenceSearchResponseProcessor.TYPE, params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(params);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MLInferenceExtBuilder && Objects.equals(params, ((MLInferenceExtBuilder) obj).params);
        }
    }
}
