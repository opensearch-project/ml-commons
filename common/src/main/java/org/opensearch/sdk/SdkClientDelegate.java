/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public interface SdkClientDelegate {

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor);

    /**
     * Read/Get a data object/document from a table/index.
     *
     * @param request  A request identifying the data object to retrieve
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor);

    /**
     * Update a data object/document in a table/index.
     *
     * @param request  A request identifying the data object to update
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor);

    /**
     * Delete a data object/document from a table/index.
     *
     * @param request  A request identifying the data object to delete
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor);

    /**
     * Search for data objects/documents in a table/index.
     *
     * @param request  A request identifying the data objects to search for
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor);
}
