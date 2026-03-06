/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ingest.GetPipelineRequest;
import org.opensearch.action.ingest.GetPipelineResponse;
import org.opensearch.action.ingest.PutPipelineRequest;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

public class MemoryContainerPipelineHelperTests extends OpenSearchTestCase {

    private Client client;
    private MLIndicesHandler indicesHandler;
    private AdminClient adminClient;
    private ClusterAdminClient clusterAdminClient;
    private IndicesAdminClient indicesAdminClient;

    @Before
    public void setUpTest() {
        client = mock(Client.class);
        indicesHandler = mock(MLIndicesHandler.class);
        adminClient = mock(AdminClient.class);
        clusterAdminClient = mock(ClusterAdminClient.class);
        indicesAdminClient = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
    }

    public void testCreateLongTermMemoryIngestPipelineCreatesResources() {
        MemoryConfiguration configuration = MemoryConfiguration
            .builder()
            .indexPrefix("prefix")
            .embeddingModelId("embedding")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(8)
            .build();

        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("missing"));
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(acknowledgedTrue());
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).createLongTermMemoryIndex(any(), any(), any(), any());

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createLongTermMemoryIngestPipeline("index", configuration, indicesHandler, client, future);

        assertTrue(future.actionGet());
        verify(indicesHandler).createLongTermMemoryIndex(eq("index-embedding"), eq("index"), eq(configuration), any());
    }

    public void testCreateLongTermMemoryIngestPipelineWithoutEmbedding() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).createLongTermMemoryIndex(eq(null), eq("index"), eq(configuration), any());

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createLongTermMemoryIngestPipeline("index", configuration, indicesHandler, client, future);

        assertTrue(future.actionGet());
    }

    public void testCreateTextEmbeddingPipelineWhenPipelineExists() {
        MemoryConfiguration configuration = MemoryConfiguration
            .builder()
            .indexPrefix("prefix")
            .embeddingModelId("embedding")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(8)
            .build();

        GetPipelineResponse response = mock(GetPipelineResponse.class);
        when(response.pipelines()).thenReturn(Collections.singletonList(null));
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any());

        AtomicBoolean putCalled = new AtomicBoolean(false);
        doAnswer(invocation -> {
            putCalled.set(true);
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(acknowledgedTrue());
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any());

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createTextEmbeddingPipeline("index-embedding", configuration, client, future);

        assertTrue(future.actionGet());
        assertFalse(putCalled.get());
    }

    public void testCreateTextEmbeddingPipelineFailure() {
        MemoryConfiguration configuration = MemoryConfiguration
            .builder()
            .indexPrefix("prefix")
            .embeddingModelId("embedding")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(8)
            .build();

        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("missing"));
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("put failure"));
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any());

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createTextEmbeddingPipeline("index-embedding", configuration, client, future);

        OpenSearchStatusException exception = expectThrows(OpenSearchStatusException.class, future::actionGet);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, exception.status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    public void testCreateHistoryIndexIfEnabled() {
        MemoryConfiguration configuration = MemoryConfiguration
            .builder()
            .indexPrefix("prefix")
            .embeddingModelId("embedding")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(8)
            .build();

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).createLongTermMemoryHistoryIndex(eq("history"), eq(configuration), any());

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createHistoryIndexIfEnabled(configuration, "history", indicesHandler, future);
        assertTrue(future.actionGet());

        MemoryConfiguration disabled = MemoryConfiguration.builder().indexPrefix("prefix").disableHistory(true).build();

        AtomicBoolean disabledCalled = new AtomicBoolean(false);
        doAnswer(invocation -> {
            disabledCalled.set(true);
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).createLongTermMemoryHistoryIndex(eq("history"), eq(disabled), any());

        PlainActionFuture<Boolean> skip = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createHistoryIndexIfEnabled(disabled, "history", indicesHandler, skip);
        assertTrue(skip.actionGet());
        assertFalse(disabledCalled.get());
    }

    private AcknowledgedResponse acknowledgedTrue() {
        return new AcknowledgedResponse(true);
    }

    // ===== Hybrid Search Pipeline Tests =====

    public void testCreateHybridSearchPipeline_Success() {
        // Pipeline doesn't exist (NOT_FOUND) → create it
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(2);
            listener.onFailure(new OpenSearchStatusException("not found", RestStatus.NOT_FOUND));
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        // Re-mock: first call (GetSearchPipeline) → NOT_FOUND, second call (PutSearchPipeline) → success
        final int[] callCount = { 0 };
        doAnswer(invocation -> {
            callCount[0]++;
            ActionListener listener = invocation.getArgument(2);
            if (callCount[0] == 1) {
                listener.onFailure(new OpenSearchStatusException("not found", RestStatus.NOT_FOUND));
            } else {
                listener.onResponse(acknowledgedTrue());
            }
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createHybridSearchPipeline("test-memory-long-term", client, future);
        assertTrue(future.actionGet());
    }

    public void testCreateHybridSearchPipeline_AlreadyExists() {
        // Pipeline already exists → skip creation
        org.opensearch.action.search.GetSearchPipelineResponse response = mock(
            org.opensearch.action.search.GetSearchPipelineResponse.class
        );
        when(response.pipelines()).thenReturn(Collections.singletonList(null));

        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createHybridSearchPipeline("test-memory-long-term", client, future);
        assertTrue(future.actionGet());
    }

    public void testCreateHybridSearchPipeline_NonNotFoundError_Propagates() {
        // Non-NOT_FOUND error (e.g., permission denied) → best-effort: log warning, return true
        OpenSearchStatusException permissionError = new OpenSearchStatusException("forbidden", RestStatus.FORBIDDEN);

        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(2);
            listener.onFailure(permissionError);
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        PlainActionFuture<Boolean> future = PlainActionFuture.newFuture();
        MemoryContainerPipelineHelper.createHybridSearchPipeline("test-memory-long-term", client, future);

        // Should succeed (best-effort) even when neural-search plugin is not available
        assertTrue(future.actionGet());
    }
}
