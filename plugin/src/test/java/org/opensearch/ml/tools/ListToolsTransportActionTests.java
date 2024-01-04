/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

    private RuntimeException exceptionToThrow;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        toolMetadataList = new ArrayList<>();
        ToolMetadata wikipediaTool = ToolMetadata
            .builder()
            .name("WikipediaTool")
            .description("Use this tool to search general knowledge on wikipedia.")
            .type("forTestingPurpose")
            .version("test")
            .build();
        toolMetadataList.add(wikipediaTool);
        mlToolsListRequest = MLToolsListRequest.builder().toolMetadataList(toolMetadataList).build();

        exceptionToThrow = new RuntimeException("Failed to get tools list");

        listToolsTransportAction = spy(new ListToolsTransportAction(transportService, actionFilters));
    }

    public void testListTools_Success() {
        listToolsTransportAction.doExecute(null, mlToolsListRequest, actionListener);
        verify(actionListener, times(1)).onResponse(any());
    }

    public void testListTools_Failure() {
        doThrow(exceptionToThrow).when(actionListener).onResponse(any(MLToolsListResponse.class));

        listToolsTransportAction.doExecute(null, mlToolsListRequest, actionListener);

        verify(actionListener, times(1)).onFailure(exceptionToThrow);
    }

}
