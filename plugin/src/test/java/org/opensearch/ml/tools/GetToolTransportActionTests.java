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
import org.opensearch.ml.common.transport.tools.MLToolGetRequest;
import org.opensearch.ml.common.transport.tools.MLToolGetResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class GetToolTransportActionTests extends OpenSearchTestCase {
    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLToolGetResponse> actionListener;

    GetToolTransportAction getToolTransportAction;
    MLToolGetRequest mlToolGetRequest;
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
            .build();
        toolMetadataList.add(wikipediaTool);
        mlToolGetRequest = MLToolGetRequest.builder().toolMetadataList(toolMetadataList).toolName("WikipediaTool").build();
        exceptionToThrow = new RuntimeException("Failed to get tool");

        getToolTransportAction = spy(new GetToolTransportAction(transportService, actionFilters));
    }

    public void testGetTool_Success() {
        getToolTransportAction.doExecute(null, mlToolGetRequest, actionListener);
        verify(actionListener, times(1)).onResponse(any());
    }

    public void testListTools_Failure() {
        doThrow(exceptionToThrow).when(actionListener).onResponse(any(MLToolGetResponse.class));

        getToolTransportAction.doExecute(null, mlToolGetRequest, actionListener);

        verify(actionListener, times(1)).onFailure(exceptionToThrow);
    }

}
