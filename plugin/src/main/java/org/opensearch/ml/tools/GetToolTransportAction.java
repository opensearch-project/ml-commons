/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tools;

import java.util.List;
import java.util.NoSuchElementException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.transport.tools.*;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetToolTransportAction extends HandledTransportAction<ActionRequest, MLToolGetResponse> {
    @Inject
    public GetToolTransportAction(TransportService transportService, ActionFilters actionFilters) {
        super(MLGetToolAction.NAME, transportService, actionFilters, MLToolGetRequest::new);
    }

    /**
     * @param task
     * @param request
     * @param listener
     */
    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLToolGetResponse> listener) {
        MLToolGetRequest mlToolGetRequest = MLToolGetRequest.fromActionRequest(request);
        String toolName = mlToolGetRequest.getToolName();
        try {
            List<ToolMetadata> toolsList = mlToolGetRequest.getToolMetadataList();
            ToolMetadata theTool = toolsList
                .stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
            listener.onResponse(MLToolGetResponse.builder().toolMetadata(theTool).build());
        } catch (Exception e) {
            log.error("Failed to get tools list", e);
            listener.onFailure(e);
        }

    }
}
