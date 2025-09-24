/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetEventRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetEventResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Transport action for getting an event from a memory container
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportGetEventAction extends HandledTransportAction<MLGetEventRequest, MLGetEventResponse> {

    final Client client;
    final NamedXContentRegistry xContentRegistry;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportGetEventAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLGetEventAction.NAME, transportService, actionFilters, MLGetEventRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLGetEventRequest request, ActionListener<MLGetEventResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String memoryContainerId = request.getMemoryContainerId();
        String eventId = request.getEventId();

        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        if (StringUtils.isBlank(eventId)) {
            actionListener.onFailure(new IllegalArgumentException("Event ID is required"));
            return;
        }

        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to access this memory container", RestStatus.FORBIDDEN)
                    );
                return;
            }

            getEventFromIndex(eventId, container, actionListener);
        }, actionListener::onFailure));
    }

    private void getEventFromIndex(String eventId, MLMemoryContainer container, ActionListener<MLGetEventResponse> actionListener) {
        try {
            MemoryConfiguration configuration = container.getConfiguration();
            String eventIndexName = configuration.getShortTermMemoryIndexName();

            GetRequest getRequest = new GetRequest(eventIndexName, eventId);

            client.get(getRequest, ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    actionListener.onFailure(new OpenSearchStatusException("Event not found", RestStatus.NOT_FOUND));
                    return;
                }

                try (
                    XContentParser parser = jsonXContent
                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
                ) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLGetEventResponse response = MLGetEventResponse.parse(parser, eventId);
                    actionListener.onResponse(response);
                } catch (Exception e) {
                    log.error("Failed to parse event response", e);
                    actionListener.onFailure(e);
                }
            }, actionListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to get event", e);
            actionListener.onFailure(e);
        }
    }

}
