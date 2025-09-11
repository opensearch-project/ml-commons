/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class IndexInsightAccessControllerHelperTests {

    @Test
    public void testConstructSimpleQueryRequest() {
        String sourceIndex = "test-index";
        SearchRequest searchRequest = IndexInsightAccessControllerHelper.constructSimpleQueryRequest(sourceIndex);

        assertEquals(sourceIndex, searchRequest.indices()[0]);
        assertEquals(1, searchRequest.source().size());
        assertEquals(MatchAllQueryBuilder.class, searchRequest.source().query().getClass());
    }

    @Test
    public void testVerifyAccessController_Success() {
        Client client = mock(Client.class);
        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        ActionListener<Boolean> actionListener = mock(ActionListener.class);
        String sourceIndex = "test-index";

        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(getMappingsResponse.getMappings()).thenReturn(Map.of("test-index", mappingMetadata));

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        IndexInsightAccessControllerHelper.verifyAccessController(client, actionListener, sourceIndex);

        verify(actionListener).onResponse(true);
    }

    @Test
    public void testVerifyAccessController_Failure() {
        Client client = mock(Client.class);
        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        ActionListener<Boolean> actionListener = mock(ActionListener.class);
        String sourceIndex = "test-index";

        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(getMappingsResponse.getMappings()).thenReturn(Map.of("test-index", mappingMetadata));

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("no permissions"));
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        IndexInsightAccessControllerHelper.verifyAccessController(client, actionListener, sourceIndex);

        ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        assertEquals("no permissions", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testVerifyAccessController_NoMatchingIndices() {
        Client client = mock(Client.class);
        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        ActionListener<Boolean> actionListener = mock(ActionListener.class);
        String sourceIndex = "abc*";

        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        when(getMappingsResponse.getMappings()).thenReturn(Map.of());

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        IndexInsightAccessControllerHelper.verifyAccessController(client, actionListener, sourceIndex);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        assertEquals("No matching indices found for: abc*", exceptionCaptor.getValue().getMessage());
    }
}
