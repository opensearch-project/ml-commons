/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.core.common.Strings;

import static org.opensearch.sdk.SdkClientUtils.unwrapAndConvertToException;

public class SdkClient {

    private final SdkClientDelegate delegate;
    private final Boolean isMultiTenancyEnabled;

    public SdkClient(SdkClientDelegate delegate, Boolean multiTenancy) {
        this.delegate = delegate;
        this.isMultiTenancyEnabled = multiTenancy;
    }

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
        validateTenantId(request.tenantId());
        return delegate.putDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
        return putDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Create/Put/Index a data object/document into a table/index.
     * @param request A request encapsulating the data object to store
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public PutDataObjectResponse putDataObject(PutDataObjectRequest request) {
        try {
            return putDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Read/Get a data object/document from a table/index.
     *
     * @param request  A request identifying the data object to retrieve
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
        return delegate.getDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Read/Get a data object/document from a table/index.
     *
     * @param request A request identifying the data object to retrieve
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request) {
        validateTenantId(request.tenantId());
        return getDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Read/Get a data object/document from a table/index.
     * @param request A request identifying the data object to retrieve
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public GetDataObjectResponse getDataObject(GetDataObjectRequest request) {
        try {
            return getDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Update a data object/document in a table/index.
     *
     * @param request  A request identifying the data object to update
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor) {
        validateTenantId(request.tenantId());
        return delegate.updateDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Update a data object/document in a table/index.
     *
     * @param request A request identifying the data object to update
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request) {
        return updateDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Update a data object/document in a table/index.
     * @param request A request identifying the data object to update
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public UpdateDataObjectResponse updateDataObject(UpdateDataObjectRequest request) {
        try {
            return updateDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Delete a data object/document from a table/index.
     *
     * @param request  A request identifying the data object to delete
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
        validateTenantId(request.tenantId());
        return delegate.deleteDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Delete a data object/document from a table/index.
     *
     * @param request A request identifying the data object to delete
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
        return deleteDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Delete a data object/document from a table/index.
     * @param request A request identifying the data object to delete
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public DeleteDataObjectResponse deleteDataObject(DeleteDataObjectRequest request) {
        try {
            return deleteDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Perform a bulk request for multiple data objects/documents in potentially multiple tables/indices.
     *
     * @param request A request identifying the bulk requests to execute
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(BulkDataObjectRequest request, Executor executor) {
        validateTenantId(request.globalTenantId());
        return delegate.bulkDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Perform a bulk request for multiple data objects/documents in potentially multiple tables/indices.
     *
     * @param request A request identifying the bulk requests to execute
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(BulkDataObjectRequest request) {
        return bulkDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Perform a bulk request for multiple data objects/documents in potentially multiple tables/indices.
     *
     * @param request A request identifying the bulk requests to execute
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public BulkDataObjectResponse bulkDataObject(BulkDataObjectRequest request) {
        try {
            return bulkDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Search for data objects/documents in a table/index.
     *
     * @param request  A request identifying the data objects to search for
     * @param executor the executor to use for asynchronous execution
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor) {
        validateTenantId(request.tenantId());
        return delegate.searchDataObjectAsync(request, executor, isMultiTenancyEnabled);
    }

    /**
     * Search for data objects/documents in a table/index.
     *
     * @param request A request identifying the data objects to search for
     * @return A completion stage encapsulating the response or exception
     */
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request) {
        return searchDataObjectAsync(request, ForkJoinPool.commonPool());
    }

    /**
     * Search for data objects/documents in a table/index.
     * @param request A request identifying the data objects to search for
     * @return A response on success. Throws unchecked exceptions or {@link OpenSearchException} wrapping the cause on checked exception.
     */
    public SearchDataObjectResponse searchDataObject(SearchDataObjectRequest request) {
        try {
            return searchDataObjectAsync(request).toCompletableFuture().join();
        } catch (CompletionException e) {
            throw ExceptionsHelper.convertToRuntime(unwrapAndConvertToException(e));
        }
    }

    /**
     * Get the delegate client implementation. 
     * @return the delegate implementation
     */
    public SdkClientDelegate getDelegate() {
        return delegate;
    }

    /**
     * Throw exception if tenantId is null and multitenancy is enabled
     * @param tenantId The tenantId from the request
     */
    private void validateTenantId(String tenantId) {
        if (Boolean.TRUE.equals(isMultiTenancyEnabled) && Strings.isNullOrEmpty(tenantId)) {
            throw new IllegalArgumentException("A tenant ID is required when multitenancy is enabled.");
        }
    }
}
