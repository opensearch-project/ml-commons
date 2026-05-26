/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Shared plumbing for "is this name already used in a tenant-scoped index?" checks used by the
 * agent and memory-container uniqueness flags. Mirrors the pattern in
 * {@code MLModelGroupManager#validateUniqueModelGroupName}: runs an exact-match query on the
 * {@code name.keyword} subfield under the tenant's scope and hands the raw {@link SearchResponse}
 * back to the caller, which is then responsible for interpreting hits, formatting the 409
 * message, and short-circuiting on blank/same names.
 *
 * <p>If the target index has not been created yet, the listener receives {@code null} (treated
 * as "no conflict possible") rather than an error.
 *
 * <p><b>Do not echo the conflicting hit's {@code _id} in the 409 response body.</b> Doing so
 * would let a caller confirm the existence of resources they cannot otherwise see by probing
 * names. The name itself is always caller-supplied input, so echoing it back is safe.
 */
@Log4j2
public final class NameUniquenessHelper {

    private NameUniquenessHelper() {}

    /**
     * Search an index for documents whose {@code name.keyword} exactly matches {@code name}, scoped
     * to the given tenant.
     *
     * @param client     node client (used only for its ThreadContext - the search itself goes
     *                   through {@code sdkClient})
     * @param sdkClient  SDK client used to issue the tenant-scoped search
     * @param indexName  index to query
     * @param name       value to match on {@code name.keyword}
     * @param tenantId   tenant scope for the search (may be null in single-tenant mode)
     * @param listener   receives the {@link SearchResponse} on success, {@code null} if the index
     *                   does not exist, or a failure otherwise
     */
    public static void searchByExactName(
        Client client,
        SdkClient sdkClient,
        String indexName,
        String name,
        String tenantId,
        ActionListener<SearchResponse> listener
    ) {
        BoolQueryBuilder query = new BoolQueryBuilder().filter(new TermQueryBuilder("name.keyword", name));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query).size(1).fetchSource(false);
        SearchRequest searchRequest = new SearchRequest(indexName).source(sourceBuilder);
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(searchRequest.indices())
            .searchSourceBuilder(searchRequest.source())
            .tenantId(tenantId)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                if (throwable != null) {
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        // Index not yet created - no duplicate possible.
                        listener.onResponse(null);
                        return;
                    }
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to search index [{}] for name uniqueness check", indexName, cause);
                    listener.onFailure(cause);
                    return;
                }
                try {
                    listener.onResponse(r.searchResponse());
                } catch (Exception e) {
                    log.error("Failed to parse search response for name uniqueness check on index [{}]", indexName, e);
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to execute name uniqueness check on index [{}]", indexName, e);
            listener.onFailure(e);
        }
    }
}
