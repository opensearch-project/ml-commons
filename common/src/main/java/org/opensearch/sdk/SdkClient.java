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
     * Create/Put/Index an object/document into a table/index.
     * @param request A request encapsulating the data to store
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<PutCustomResponse> putCustomAsync(PutCustomRequest request);

    /**
     * Create/Put/Index an object/document into a table/index.
     * @param request A request encapsulating the data to store
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    default PutCustomResponse putCustom(PutCustomRequest request) {
        try {
            return putCustomAsync(request).toCompletableFuture().join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }

    /**
     * Read/Get an object/document from a table/index.
     * @param request A request identifying the data to retrieve
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    public CompletionStage<GetCustomResponse> getCustomAsync(GetCustomRequest request);

    /**
     * Read/Get an object/document from a table/index.
     * @param request A request identifying the data to retrieve
     * @return A response on success.
     * @throws {@link OpenSearchException} wrapping the cause on exception.
     */
    default GetCustomResponse getCustom(GetCustomRequest request) {
        try {
            return getCustomAsync(request).toCompletableFuture().join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }

    /**
     * Delete an object/document from a table/index.
     * @param request A request identifying the data to delete
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<DeleteCustomResponse> deleteCustomAsync(DeleteCustomRequest request);

    /**
     * Delete an object/document from a table/index.
     * @param request A request identifying the data to delete
     * @return A completion stage encapsulating the response or exception
     */
    default DeleteCustomResponse deleteCustom(DeleteCustomRequest request) {
        try {
            return deleteCustomAsync(request).toCompletableFuture().join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OpenSearchException(cause);
        }
    }
    
}
