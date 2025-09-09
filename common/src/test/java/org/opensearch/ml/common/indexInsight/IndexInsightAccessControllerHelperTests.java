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

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.transport.client.Client;

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
    public void testVerifyAccessControllerSuccess() {
        Client client = mock(Client.class);
        ActionListener<Boolean> actionListener = mock(ActionListener.class);
        String sourceIndex = "test-index";

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(SearchResponse.class));
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        IndexInsightAccessControllerHelper.verifyAccessController(client, actionListener, sourceIndex);

        verify(actionListener).onResponse(true);
    }

    @Test
    public void testVerifyAccessControllerFailure() {
        Client client = mock(Client.class);
        ActionListener<Boolean> actionListener = mock(ActionListener.class);
        String sourceIndex = "test-index";

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
}
