/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.transport.tools.MLToolsListRequest;
import org.opensearch.ml.common.transport.tools.MLToolsListResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class ListToolsTransportActionTests extends OpenSearchTestCase {
    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLToolsListResponse> actionListener;

    ListToolsTransportAction listToolsTransportAction;
    MLToolsListRequest mlToolsListRequest;
    private List<ToolMetadata> toolMetadataList;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        toolMetadataList = new ArrayList<>();
        ToolMetadata wikipediaTool = ToolMetadata
            .builder()
            .name("WikipediaTool")
            .description("Use this tool to search general knowledge on wikipedia.")
            .build();
        toolMetadataList.add(wikipediaTool);
        mlToolsListRequest = MLToolsListRequest.builder().toolMetadataList(toolMetadataList).build();

        listToolsTransportAction = spy(new ListToolsTransportAction(transportService, actionFilters));
    }

    public void testListTools_Success() {
        MLToolsListResponse mlToolsListResponse = prepareResponse();

        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlToolsListResponse);
            return null;
        }).when(listToolsTransportAction).doExecute(any(), eq(mlToolsListRequest), any());

        listToolsTransportAction.doExecute(null, mlToolsListRequest, actionListener);

        verify(actionListener, times(1)).onResponse(refEq(mlToolsListResponse));

    }

    private MLToolsListResponse prepareResponse() {
        return MLToolsListResponse.builder().toolMetadata(toolMetadataList).build();
    }
}
