/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.OpenSearchException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public interface SdkClient {

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request);

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    default PutDataObjectResponse putDataObject(PutDataObjectRequest request) {
        try {
            return putDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }

    /**
     * Read/Get a data object/document from a table/index.
     * @param request A request identifying the data object to retrieve
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request);

    /**
     * Read/Get a data object/document from a table/index.
     * @param request A request identifying the data object to retrieve
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    default GetDataObjectResponse getDataObject(GetDataObjectRequest request) {
        try {
            return getDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }

    /**
     * Delete a data object/document from a table/index.
     * @param request A request identifying the data object to delete
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request);

    /**
     * Delete a data object/document from a table/index.
     * @param request A request identifying the data object to delete
     * @return A completion stage encapsulating the response or exception
     */
    default DeleteDataObjectResponse deleteDataObject(DeleteDataObjectRequest request) {
        try {
            return deleteDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }
}
