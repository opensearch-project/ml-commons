/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.transport.tools.MLListToolsAction;
import org.opensearch.ml.common.transport.tools.MLToolsListRequest;
import org.opensearch.ml.common.transport.tools.MLToolsListResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ListToolsTransportAction extends HandledTransportAction<ActionRequest, MLToolsListResponse> {
    @Inject
    public ListToolsTransportAction(TransportService transportService, ActionFilters actionFilters) {
        super(MLListToolsAction.NAME, transportService, actionFilters, MLToolsListRequest::new);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLToolsListResponse> listener) {
        MLToolsListRequest mlToolsGetRequest = MLToolsListRequest.fromActionRequest(request);

        List<ToolMetadata> toolsList = mlToolsGetRequest.getToolMetadataList();
        try {
            listener.onResponse(MLToolsListResponse.builder().toolMetadata(toolsList).build());
        } catch (Exception e) {
            log.error("Failed to get tools list", e);
            listener.onFailure(e);
        }
    }
}
