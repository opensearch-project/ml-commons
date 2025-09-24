/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TRAIN_THREAD_POOL;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.ShortTermMemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportCreateEventAction extends HandledTransportAction<MLCreateEventRequest, MLCreateEventResponse> {

    final Client client;
    final NamedXContentRegistry xContentRegistry;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    // Extracted services
    final MemoryProcessingService memoryProcessingService;
    final MemorySearchService memorySearchService;
    final MemoryOperationsService memoryOperationsService;
    final ThreadPool threadPool;

    @Inject
    public TransportCreateEventAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper,
        ThreadPool threadPool
    ) {
        this(
            transportService,
            actionFilters,
            client,
            xContentRegistry,
            mlFeatureEnabledSetting,
            memoryContainerHelper,
            new MemoryProcessingService(client, xContentRegistry),
            new MemorySearchService(client),
            new MemoryOperationsService(client),
            threadPool
        );
    }

    TransportCreateEventAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper,
        MemoryProcessingService memoryProcessingService,
        MemorySearchService memorySearchService,
        MemoryOperationsService memoryOperationsService,
        ThreadPool threadPool
    ) {
        super(MLCreateEventAction.NAME, transportService, actionFilters, MLCreateEventRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
        this.memoryProcessingService = memoryProcessingService;
        this.memorySearchService = memorySearchService;
        this.memoryOperationsService = memoryOperationsService;
        this.threadPool = threadPool;
    }

    @Override
    protected void doExecute(Task task, MLCreateEventRequest request, ActionListener<MLCreateEventResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        MLCreateEventInput input = request.getMlCreateEventInput();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Memory input is required"));
            return;
        }

        String memoryContainerId = input.getMemoryContainerId();

        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to create events in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            createNewSessionIfAbsent(input, container, user, actionListener);
        }, actionListener::onFailure));
    }

    private void createNewSessionIfAbsent(
        MLCreateEventInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLCreateEventResponse> actionListener
    ) {
        try {
            List<MessageInput> messages = input.getMessages();

            MemoryConfiguration configuration = container.getConfiguration();

            boolean userProvidedSessionId = input.getNamespace() != null && input.getNamespace().containsKey(SESSION_ID_FIELD);

            if (!userProvidedSessionId
                && input.getMemoryType() == ShortTermMemoryType.CONVERSATIONAL
                && !configuration.isDisableSession()) {
                IndexRequest indexRequest = new IndexRequest(configuration.getSessionIndexName());
                // TODO: use LLM to summarize first user message
                String summary = messages.get(0).getContentText();
                indexRequest.source(Map.of(SUMMARY_FIELD, summary, NAMESPACE_FIELD, input.getNamespace()));
                client.index(indexRequest, ActionListener.wrap(r -> {
                    input.getNamespace().put(SESSION_ID_FIELD, r.getId());
                    processAndIndexMemory(input, container, user, actionListener);
                }, e -> actionListener.onFailure(e)));
            } else {
                processAndIndexMemory(input, container, user, actionListener);
            }
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }

    private void processAndIndexMemory(
        MLCreateEventInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLCreateEventResponse> actionListener
    ) {
        try {
            List<MessageInput> messages = input.getMessages();

            boolean infer = input.isInfer();
            MemoryConfiguration memoryConfig = container.getConfiguration();
            boolean hasLlmModel = memoryConfig != null && memoryConfig.getLlmId() != null;

            if (infer && !hasLlmModel) {
                actionListener.onFailure(new IllegalArgumentException(INFER_REQUIRES_LLM_MODEL_ERROR));
                return;
            }

            String shortTermMemoryIndex = container.getConfiguration().getShortTermMemoryIndexName();
            IndexRequest indexRequest = createShortTermMemoryRequest(shortTermMemoryIndex, input);

            client.index(indexRequest, ActionListener.wrap(r -> {
                MLCreateEventResponse response = MLCreateEventResponse.builder().eventId(r.getId()).sessionId(input.getSessionId()).build();
                actionListener.onResponse(response);

                if (infer) {
                    threadPool.executor(TRAIN_THREAD_POOL).execute(() -> {
                        extractLongTermMemory(input, container, user, ActionListener.wrap(res -> { log.info(res.toString()); }, e -> {
                            log.error("Failed to extract longTermMemory id from memory container", e);
                        }));
                    });
                }
            }, actionListener::onFailure));

        } catch (Exception e) {
            log.error("Failed to create event", e);
            actionListener.onFailure(e);
        }
    }

    private IndexRequest createShortTermMemoryRequest(String shortTermMemoryIndex, MLCreateEventInput mlCreateEventInput) {
        IndexRequest indexRequest = new IndexRequest(shortTermMemoryIndex);

        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.startObject();

            // Serialize event content (this duplicates MLCreateEventInput.toXContent() logic
            // but we need to add timestamps, so we can't use toXContent directly)
            if (mlCreateEventInput.getMemoryContainerId() != null) {
                builder.field(MEMORY_CONTAINER_ID_FIELD, mlCreateEventInput.getMemoryContainerId());
            }
            if (mlCreateEventInput.getMemoryType() != null) {
                builder.field(MEMORY_TYPE_FIELD, mlCreateEventInput.getMemoryType());
            }
            if (mlCreateEventInput.getMessages() != null && !mlCreateEventInput.getMessages().isEmpty()) {
                builder.startArray(MESSAGES_FIELD);
                for (MessageInput message : mlCreateEventInput.getMessages()) {
                    message.toXContent(builder, ToXContent.EMPTY_PARAMS);
                }
                builder.endArray();
            }
            if (mlCreateEventInput.getData() != null) {
                builder.field(DATA_FIELD, mlCreateEventInput.getData());
            }
            if (mlCreateEventInput.getNamespace() != null && !mlCreateEventInput.getNamespace().isEmpty()) {
                builder.field(NAMESPACE_FIELD, mlCreateEventInput.getNamespace());
            }
            builder.field(INFER_FIELD, mlCreateEventInput.isInfer());
            if (mlCreateEventInput.getMetadata() != null && !mlCreateEventInput.getMetadata().isEmpty()) {
                builder.field(METADATA_FIELD, mlCreateEventInput.getMetadata());
            }

            // Add timestamps
            Instant now = Instant.now();
            builder.field(CREATED_TIME_FIELD, now.toEpochMilli());
            builder.field(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());

            builder.endObject();

            indexRequest.source(builder);
            return indexRequest;
        } catch (IOException e) {
            logger.error("Failed to build index request source", e);
            throw new RuntimeException("Failed to build index request", e);
        }
    }

    private void extractLongTermMemory(
        MLCreateEventInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLCreateEventResponse> actionListener
    ) {
        List<MessageInput> messages = input.getMessages();
        log.debug("Processing {} messages for fact extraction", messages.size());

        List<MemoryStrategy> strategies = container.getConfiguration().getStrategies();
        MemoryConfiguration memoryConfig = container.getConfiguration();

        for (MemoryStrategy strategy : strategies) {
            if (strategy.isEnabled()) {
                Map<String, String> strategyNameSpace = getStrategyNameSpace(strategy, input.getNamespace());
                if (strategyNameSpace.size() != strategy.getNamespace().size()) {
                    log.info("Skipping strategy {} due to missing namespace", strategy.getId());
                } else {
                    memoryProcessingService.runMemoryStrategy(strategy, messages, memoryConfig, ActionListener.wrap(facts -> {
                        storeLongTermMemory(strategy, strategyNameSpace, input, messages, user, facts, memoryConfig, actionListener);
                    }, e -> {
                        log.error("Failed to extract facts with LLM", e);
                        actionListener.onFailure(new OpenSearchException("Failed to extract facts: " + e.getMessage(), e));
                    }));
                }
            }
        }
    }

    private Map<String, String> getStrategyNameSpace(MemoryStrategy strategy, Map<String, String> namespace) {
        Map<String, String> strategyNamespace = new HashMap<>();
        for (String key : strategy.getNamespace()) {
            if (namespace.containsKey(key)) {
                strategyNamespace.put(key, namespace.get(key));
            }
        }
        return strategyNamespace;
    }

    private void storeLongTermMemory(
        MemoryStrategy strategy,
        Map<String, String> strategyNameSpace,
        MLCreateEventInput input,
        List<MessageInput> messages,
        User user,
        List<String> facts,
        MemoryConfiguration memoryConfig,
        ActionListener<MLCreateEventResponse> actionListener
    ) {
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        // Search for similar facts and make memory decisions
        if (!facts.isEmpty() && memoryConfig != null && memoryConfig.getLlmId() != null) {
            log.debug("Searching for similar facts in session to make memory decisions");
            memorySearchService.searchSimilarFactsForSession(strategy, input, facts, memoryConfig, ActionListener.wrap(allSearchResults -> {
                log.debug("Found {} total similar facts across all {} new facts", allSearchResults.size(), facts.size());

                if (allSearchResults.size() > 0) {
                    memoryProcessingService.makeMemoryDecisions(facts, allSearchResults, memoryConfig, ActionListener.wrap(decisions -> {
                        memoryOperationsService
                            .executeMemoryOperations(
                                decisions,
                                memoryConfig,
                                strategyNameSpace,
                                user,
                                input,
                                memoryConfig,
                                ActionListener.wrap(operationResults -> {
                                    MLCreateEventResponse response = MLCreateEventResponse
                                        .builder()
                                        .eventId(null)
                                        .sessionId(input.getSessionId())
                                        .build();
                                    actionListener.onResponse(response);
                                }, actionListener::onFailure)
                            );
                    }, e -> {
                        log.error("Failed to make memory decisions", e);
                        actionListener.onFailure(new OpenSearchException("Failed to make memory decisions: " + e.getMessage(), e));
                    }));
                } else {
                    List<MemoryDecision> decisions = new ArrayList<>();
                    for (int i = 0; i < facts.size(); i++) {
                        decisions.add(MemoryDecision.builder().id("fact_" + i).event(MemoryEvent.ADD).text(facts.get(i)).build());
                    }
                    memoryOperationsService
                        .executeMemoryOperations(
                            decisions,
                            memoryConfig,
                            strategyNameSpace,
                            user,
                            input,
                            memoryConfig,
                            ActionListener.wrap(operationResults -> {
                                MLCreateEventResponse response = MLCreateEventResponse
                                    .builder()
                                    .eventId(null)
                                    .sessionId(input.getSessionId())
                                    .build();
                                actionListener.onResponse(response);
                            }, actionListener::onFailure)
                        );
                }
            }, e -> {
                log.error("Failed to search similar facts", e);
                actionListener.onFailure(new OpenSearchException("Failed to search similar facts: " + e.getMessage(), e));
            }));
        } else {
            // No memory decisions needed
            memoryOperationsService
                .createFactMemoriesFromList(
                    facts,
                    memoryConfig.getLongMemoryIndexName(),
                    input,
                    strategyNameSpace,
                    user,
                    indexRequests,
                    memoryInfos
                );
        }
    }

}
