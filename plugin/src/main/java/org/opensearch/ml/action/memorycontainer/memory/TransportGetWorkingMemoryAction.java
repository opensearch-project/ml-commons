/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memories.MLWorkingMemory;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportGetWorkingMemoryAction extends HandledTransportAction<MLGetWorkingMemoryRequest, MLGetWorkingMemoryResponse> {
    private static final Logger log = LogManager.getLogger(TransportGetWorkingMemoryAction.class);

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportGetWorkingMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLGetWorkingMemoryAction.NAME, transportService, actionFilters, MLGetWorkingMemoryRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLGetWorkingMemoryRequest request, ActionListener<MLGetWorkingMemoryResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        String memoryContainerId = request.getMemoryContainerId();
        String workingMemoryId = request.getWorkingMemoryId();

        // First, get the memory container to determine the index name
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            MemoryConfiguration configuration = container.getConfiguration();
            String workingMemoryIndex = configuration.getWorkingMemoryIndexName();

            // Get the working memory document
            GetRequest getRequest = new GetRequest(workingMemoryIndex, workingMemoryId);
            ActionListener<GetResponse> getResponseActionListener = ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    actionListener.onFailure(new OpenSearchStatusException("Working memory not found", RestStatus.NOT_FOUND));
                    return;
                }
                try {
                    // Parse the source and create MLAddMemoriesInput from it
                    MLWorkingMemory workingMemory = parseWorkingMemory(getResponse.getSourceAsString());
                    MLGetWorkingMemoryResponse response = MLGetWorkingMemoryResponse.builder().workingMemory(workingMemory).build();
                    actionListener.onResponse(response);
                } catch (Exception e) {
                    log.error("Failed to parse working memory", e);
                    actionListener.onFailure(e);
                }
            }, actionListener::onFailure);
            memoryContainerHelper.getData(configuration, getRequest, getResponseActionListener);
        }, actionListener::onFailure));
    }

    private MLWorkingMemory parseWorkingMemory(String source) {
        try (XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, source)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLWorkingMemory.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
