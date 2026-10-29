/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import java.io.IOException;
import java.util.ArrayList;
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

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (this.params == null) {
            return super.toXContent(builder, params);
        }
        // Other response processors, such as agentic_context, add their own entries to the ext section of the
        // response. The section is written once from the list of extensions, so the ml_inference output has to
        // join that list instead of being written as a second ext object.
        SearchResponseSections sections = getInternalResponse();
        List<SearchExtBuilder> extensions = new ArrayList<>(sections.getSearchExtBuilders());
        extensions.add(new MLInferenceExtBuilder(this.params));
        SearchResponseSections merged = new SearchResponseSections(
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
        return new SearchResponse(
            merged,
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
    private static final class MLInferenceExtBuilder extends SearchExtBuilder {
        private final Map<String, Object> params;

        private MLInferenceExtBuilder(Map<String, Object> params) {
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
