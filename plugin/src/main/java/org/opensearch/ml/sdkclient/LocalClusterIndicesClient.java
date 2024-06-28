/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.get.GetResult;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;

import lombok.extern.log4j.Log4j2;

/**
 * An implementation of {@link SdkClient} that stores data in a local OpenSearch cluster using the Node Client.
 */
@Log4j2
public class LocalClusterIndicesClient implements SdkClient {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    /**
     * Instantiate this object with an OpenSearch client.
     * @param client The client to wrap
     * @param xContentRegistry the registry of XContent objects
     */
    public LocalClusterIndicesClient(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<PutDataObjectResponse>) () -> {
            try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
                log.info("Indexing data object in {}", request.index());
                IndexResponse indexResponse = client
                    .index(
                        new IndexRequest(request.index())
                            .setRefreshPolicy(IMMEDIATE)
                            .source(request.dataObject().toXContent(sourceBuilder, EMPTY_PARAMS))
                    )
                    .actionGet();
                log.info("Creation status for id {}: {}", indexResponse.getId(), indexResponse.getResult());
                return new PutDataObjectResponse.Builder().id(indexResponse.getId()).parser(createParser(indexResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException(
                    "Failed to parse data object to put in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                log.info("Getting {} from {}", request.id(), request.index());
                GetResponse getResponse = client
                    .get(new GetRequest(request.index(), request.id()).fetchSourceContext(request.fetchSourceContext()))
                    .actionGet();
                if (getResponse == null) {
                    log.info("Null GetResponse");
                    return new GetDataObjectResponse.Builder()
                        .id(request.id())
                        .parser(
                            createParser(
                                new GetResponse(
                                    new GetResult(
                                        request.index(),
                                        request.id(),
                                        UNASSIGNED_SEQ_NO,
                                        UNASSIGNED_PRIMARY_TERM,
                                        -1,
                                        false,
                                        null,
                                        null,
                                        null
                                    )
                                )
                            )
                        )
                        .build();
                }
                log.info("Retrieved data object");
                return new GetDataObjectResponse.Builder()
                    .id(getResponse.getId())
                    .parser(createParser(getResponse))
                    .source(getResponse.getSource())
                    .build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parser creation error
                throw new OpenSearchStatusException(
                    "Failed to create parser for data object retrieved from index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<UpdateDataObjectResponse>) () -> {
            try (XContentBuilder sourceBuilder = XContentFactory.jsonBuilder()) {
                log.info("Updating {} from {}", request.id(), request.index());
                UpdateResponse updateResponse = client
                    .update(
                        new UpdateRequest(request.index(), request.id()).doc(request.dataObject().toXContent(sourceBuilder, EMPTY_PARAMS))
                    )
                    .actionGet();
                log.info("Update status for id {}: {}", updateResponse.getId(), updateResponse.getResult());
                return new UpdateDataObjectResponse.Builder().id(updateResponse.getId()).parser(createParser(updateResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException(
                    "Failed to parse data object to update in index " + request.index(),
                    RestStatus.BAD_REQUEST
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
            try {
                log.info("Deleting {} from {}", request.id(), request.index());
                DeleteResponse deleteResponse = client.delete(new DeleteRequest(request.index(), request.id())).actionGet();
                log.info("Deletion status for id {}: {}", deleteResponse.getId(), deleteResponse.getResult());
                return new DeleteDataObjectResponse.Builder().id(deleteResponse.getId()).parser(createParser(deleteResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException(
                    "Failed to parse data object to deletion response in index " + request.index(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<SearchDataObjectResponse>) () -> {
            log.info("Searching {}", Arrays.toString(request.indices()), null);
            SearchResponse searchResponse = client.search(new SearchRequest(request.indices(), request.searchSourceBuilder())).actionGet();
            log.info("Search returned {} hits", searchResponse.getHits().getTotalHits());
            try {
                return new SearchDataObjectResponse.Builder().parser(createParser(searchResponse)).build();
            } catch (IOException e) {
                // Rethrow unchecked exception on XContent parsing error
                throw new OpenSearchStatusException(
                    "Failed to search indices " + Arrays.toString(request.indices()),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
        }), executor);
    }

    private XContentParser createParser(ToXContent obj) throws IOException {
        return jsonXContent
            .createParser(xContentRegistry, DeprecationHandler.IGNORE_DEPRECATIONS, Strings.toString(MediaTypeRegistry.JSON, obj));
    }
}
