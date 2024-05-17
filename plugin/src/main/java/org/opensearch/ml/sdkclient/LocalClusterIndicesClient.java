/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.opensearch.action.DocWriteResponse.Result.CREATED;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;

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
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
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
                return new PutDataObjectResponse.Builder().id(indexResponse.getId()).created(indexResponse.getResult() == CREATED).build();
            } catch (Exception e) {
                throw new OpenSearchException(e);
            }
        }));
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<GetDataObjectResponse>) () -> {
            try {
                log.info("Getting {} from {}", request.id(), request.index());
                GetResponse getResponse = client.get(new GetRequest(request.index(), request.id())).actionGet();
                if (!getResponse.isExists()) {
                    throw new OpenSearchStatusException("Data object with id " + request.id() + " not found", RestStatus.NOT_FOUND);
                }
                XContentParser parser = jsonXContent
                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString());
                log.info("Retrieved data object");
                return new GetDataObjectResponse.Builder().id(getResponse.getId()).parser(parser).build();
            } catch (OpenSearchStatusException notFound) {
                throw notFound;
            } catch (Exception e) {
                throw new OpenSearchException(e);
            }
        }));
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged((PrivilegedAction<DeleteDataObjectResponse>) () -> {
            try {
                log.info("Deleting {} from {}", request.id(), request.index());
                DeleteResponse deleteResponse = client.delete(new DeleteRequest(request.index(), request.id())).actionGet();
                log.info("Deletion status for id {}: {}", deleteResponse.getId(), deleteResponse.getResult());
                return new DeleteDataObjectResponse.Builder()
                    .id(deleteResponse.getId())
                    .shardId(deleteResponse.getShardId())
                    .deleted(deleteResponse.getResult() == DELETED)
                    .build();
            } catch (Exception e) {
                throw new OpenSearchException(e);
            }
        }));
    }
}
