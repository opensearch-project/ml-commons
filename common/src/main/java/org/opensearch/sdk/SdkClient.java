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
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public interface SdkClient {

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor);

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A completion stage encapsulating the response or exception
     */
    default CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
        return putDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A response on success. Throws {@link OpenSearchException} wrapping the cause on exception.
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
     * @param executor the executor to use for asynchronous execution
     * @return A response on success. Throws {@link OpenSearchException} wrapping the cause on exception.
     */
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor);

    /**
     * Read/Get a data object/document from a table/index.
     * @param request A request identifying the data object to retrieve
     * @return A response on success. Throws {@link OpenSearchException} wrapping the cause on exception.
     */
    default CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request){
        return getDataObjectAsync(request, ForkJoinPool.commonPool());        
    }

    /**
     * Read/Get a data object/document from a table/index.
     * @param request A request identifying the data object to retrieve
     * @return A response on success. Throws {@link OpenSearchException} wrapping the cause on exception.
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
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor);

    /**
     * Delete a data object/document from a table/index.
     * @param request A request identifying the data object to delete
     * @return A completion stage encapsulating the response or exception
     */
    default CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
        return deleteDataObjectAsync(request, ForkJoinPool.commonPool());        
    }

    /**
     * Delete a data object/document from a table/index.
     * @param request A request identifying the data object to delete
     * @return A response on success. Throws {@link OpenSearchException} wrapping the cause on exception.
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
