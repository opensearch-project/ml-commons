/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.sdkclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.opensearch.remote.metadata.client.BulkDataObjectRequest;
import org.opensearch.remote.metadata.client.BulkDataObjectResponse;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClientDelegate;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;

/**
 * A decorating {@link SdkClientDelegate} that ensures all returned {@link CompletionStage}
 * instances deliver their results on a specified executor rather than on the thread that
 * originally completed the future (typically an OpenSearch transport thread).
 * <p>
 * This prevents thread pool deadlocks: when {@code LocalClusterIndicesClient} completes
 * futures on a transport thread and callers block that same thread with {@code actionGet()},
 * the thread is waiting for itself. By forcing a thread hop, downstream continuations
 * ({@code .whenComplete()}, {@code .thenApply()}, etc.) execute on a safe, non-transport pool.
 */
public class DelegatingAsyncSdkClient implements SdkClientDelegate {

    private final SdkClientDelegate delegate;
    private final Executor completionExecutor;

    /**
     * @param delegate           the underlying SdkClientDelegate (e.g., LocalClusterIndicesClient)
     * @param completionExecutor the executor on which CompletionStage results are delivered
     */
    public DelegatingAsyncSdkClient(SdkClientDelegate delegate, Executor completionExecutor) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (completionExecutor == null) {
            throw new IllegalArgumentException("completionExecutor must not be null");
        }
        this.delegate = delegate;
        this.completionExecutor = completionExecutor;
    }

    @Override
    public boolean supportsMetadataType(String metadataType) {
        return delegate.supportsMetadataType(metadataType);
    }

    @Override
    public void initialize(Map<String, String> metadataSettings) {
        delegate.initialize(metadataSettings);
    }

    @Override
    public CompletionStage<PutDataObjectResponse> putDataObjectAsync(
        PutDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.putDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<GetDataObjectResponse> getDataObjectAsync(
        GetDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.getDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(
        UpdateDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.updateDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(
        DeleteDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.deleteDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<BulkDataObjectResponse> bulkDataObjectAsync(
        BulkDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.bulkDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(
        SearchDataObjectRequest request,
        Executor executor,
        Boolean isMultiTenancyEnabled
    ) {
        return hopThread(delegate.searchDataObjectAsync(request, executor, isMultiTenancyEnabled));
    }

    @Override
    public CompletionStage<Boolean> isGlobalResource(String index, String id, Executor executor, Boolean isMultiTenancyEnabled) {
        return hopThread(delegate.isGlobalResource(index, id, executor, isMultiTenancyEnabled));
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    /**
     * Forces a thread hop so that any continuation chained on the returned stage
     * executes on {@code completionExecutor} rather than the thread that called
     * {@code future.complete()} (which is typically an OpenSearch transport thread).
     * <p>
     * Uses {@code handleAsync} to cover BOTH the success and failure paths -- unlike
     * {@code thenApplyAsync}, which only hops on success and short-circuits exceptions
     * on the completing thread.
     * <p>
     * We create a fresh {@code CompletableFuture} rather than returning the stage from
     * {@code handleAsync} directly, to avoid wrapping exceptions in {@code CompletionException}.
     * The original exception type is preserved exactly as thrown by the SDK.
     */
    private <T> CompletionStage<T> hopThread(CompletionStage<T> stage) {
        CompletableFuture<T> result = new CompletableFuture<>();
        stage.handleAsync((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            } else {
                result.complete(value);
            }
            return null;
        }, completionExecutor);
        return result;
    }
}
