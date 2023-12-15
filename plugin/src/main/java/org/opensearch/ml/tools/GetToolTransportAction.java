/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tools;

import java.util.ArrayList;
import java.util.Arrays;
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
        List<ToolMetadata> externalTools = mlToolGetRequest.getExternalTools();
        List<ToolMetadata> toolsList = new ArrayList<>(
            Arrays
                .asList(
                    ToolMetadata.builder().name("LanguageModelTool").description("Useful for answering any general questions.").build(),
                    ToolMetadata.builder().name("MathTool").description("Use this tool to calculate any math problem.").build(),
                    ToolMetadata
                        .builder()
                        .name("SearchIndexTool")
                        .description(
                            "Useful for when you don't know answer for some question or need to search my private data in OpenSearch index."
                        )
                        .build(),
                    ToolMetadata
                        .builder()
                        .name("SearchWikipediaTool")
                        .description("Useful when you need to use this tool to search general knowledge on wikipedia.")
                        .build()
                )
        );
        toolsList.addAll(externalTools);
        ToolMetadata theTool = toolsList
            .stream()
            .filter(tool -> tool.getName().equals(toolName))
            .findFirst()
            .orElseThrow(NoSuchElementException::new);
        try {
            listener.onResponse(MLToolGetResponse.builder().toolMetadata(theTool).build());
        } catch (Exception e) {
            log.error("Failed to get tools list", e);
            listener.onFailure(e);
        }

    }
}
