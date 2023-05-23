/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class SearchTaskTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;

    @Mock
    NamedXContentRegistry namedXContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    SearchRequest searchRequest;

    @Mock
    ActionListener<SearchResponse> actionListener;

    MLSearchHandler mlSearchHandler;
    SearchTaskTransportAction searchTaskTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlSearchHandler = spy(new MLSearchHandler(client, namedXContentRegistry, modelAccessControlHelper));
        searchTaskTransportAction = new SearchTaskTransportAction(transportService, actionFilters, mlSearchHandler);
    }

    public void test_DoExecute() {
        searchTaskTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
    }
}
