/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.AGENTIC_MEMORY_THREAD_POOL;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
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
import org.opensearch.ml.common.memorycontainer.PayloadType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryResult;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportAddMemoriesAction extends HandledTransportAction<MLAddMemoriesRequest, MLAddMemoriesResponse> {

    final Client client;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    // Extracted services
    final MemoryProcessingService memoryProcessingService;
    final MemorySearchService memorySearchService;
    final MemoryOperationsService memoryOperationsService;
    final ThreadPool threadPool;

    @Inject
    public TransportAddMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper,
        ThreadPool threadPool
    ) {
        super(MLAddMemoriesAction.NAME, transportService, actionFilters, MLAddMemoriesRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;

        // Initialize services
        this.memoryProcessingService = new MemoryProcessingService(client, xContentRegistry, memoryContainerHelper);
        this.memorySearchService = new MemorySearchService(memoryContainerHelper);
        this.memoryOperationsService = new MemoryOperationsService(memoryContainerHelper);
        this.threadPool = threadPool;
    }

    @Override
    protected void doExecute(Task task, MLAddMemoriesRequest request, ActionListener<MLAddMemoriesResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String ownerId = memoryContainerHelper.getOwnerId(user);
        MLAddMemoriesInput input = request.getMlAddMemoryInput();
        input.setOwnerId(ownerId);

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Memory input is required"));
            return;
        }

        String memoryContainerId = input.getMemoryContainerId();

        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        memoryContainerHelper.getMemoryContainer(memoryContainerId, request.getMlAddMemoryInput().getTenantId(), ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }
            createNewSessionIfAbsent(input, container, user, actionListener);
        }, actionListener::onFailure));
    }

    private void createNewSessionIfAbsent(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        try {
            container.getConfiguration().getParameters().putAll(input.getParameters()); // merge user provided parameters
            List<MessageInput> messages = input.getMessages();

            MemoryConfiguration configuration = container.getConfiguration();

            boolean userProvidedSessionId = input.getNamespace() != null && input.getNamespace().containsKey(SESSION_ID_FIELD);

            if (!userProvidedSessionId
                && input.getPayloadType() == PayloadType.CONVERSATIONAL
                && !configuration.isDisableSession()
                && configuration.getLlmId() != null) {
                IndexRequest indexRequest = new IndexRequest(configuration.getSessionIndexName());
                // TODO: use LLM to summarize first user message
                ActionListener<String> summaryListener = ActionListener.wrap(summary -> {
                    Instant now = Instant.now();
                    indexRequest
                        .source(
                            Map
                                .of(
                                    OWNER_ID_FIELD,
                                    input.getOwnerId(),
                                    MEMORY_CONTAINER_ID_FIELD,
                                    input.getMemoryContainerId(),
                                    SUMMARY_FIELD,
                                    summary,
                                    NAMESPACE_FIELD,
                                    input.getNamespace(),
                                    CREATED_TIME_FIELD,
                                    now.getEpochSecond(),
                                    LAST_UPDATED_TIME_FIELD,
                                    now.getEpochSecond()
                                )
                        );
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    ActionListener<IndexResponse> responseActionListener = ActionListener.<IndexResponse>wrap(r -> {
                        input.getNamespace().put(SESSION_ID_FIELD, r.getId());
                        processAndIndexMemory(input, container, user, actionListener);
                    }, e -> {
                        log.error("Failed to index session data", e);
                        actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                    });
                    memoryContainerHelper.indexData(configuration, indexRequest, responseActionListener);
                }, exception -> {
                    // Preserve client errors (4XX) with their detailed messages
                    if (exception instanceof OpenSearchStatusException) {
                        OpenSearchStatusException osException = (OpenSearchStatusException) exception;
                        if (osException.status().getStatus() >= 400 && osException.status().getStatus() < 500) {
                            actionListener.onFailure(exception);
                            return;
                        }
                    }
                    // Wrap server errors and unexpected exceptions
                    log.error("Failed to summarize messages for session creation", exception);
                    actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                });

                memoryProcessingService.summarizeMessages(container.getConfiguration(), messages, summaryListener);
            } else {
                processAndIndexMemory(input, container, user, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to create session", e);
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void processAndIndexMemory(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        try {
            boolean infer = input.isInfer();
            MemoryConfiguration memoryConfig = container.getConfiguration();
            boolean hasLlmModel = memoryConfig != null && memoryConfig.getLlmId() != null;

            if (infer && !hasLlmModel) {
                actionListener.onFailure(new IllegalArgumentException(INFER_REQUIRES_LLM_MODEL_ERROR));
                return;
            }

            String workingMemoryIndex = container.getConfiguration().getWorkingMemoryIndexName();
            IndexRequest indexRequest = createWorkingMemoryRequest(workingMemoryIndex, input);

            ActionListener<IndexResponse> responseActionListener = ActionListener.wrap(r -> {
                List<MemoryResult> allResults = new ArrayList<>();
                // allResults.add(MemoryResult.builder().memoryId(r.getId()).build());
                MLAddMemoriesResponse response = MLAddMemoriesResponse
                    .builder()
                    .results(allResults)
                    .sessionId(input.getSessionId())
                    .workingMemoryId(r.getId())
                    .build();
                actionListener.onResponse(response);

                if (infer) {
                    threadPool.executor(AGENTIC_MEMORY_THREAD_POOL).execute(() -> {
                        try {
                            extractLongTermMemory(input, container, user, ActionListener.wrap(res -> {}, e -> {
                                log.error("Failed to extract longTermMemory id from memory container", e);
                            }));
                        } catch (Exception e) {
                            memoryOperationsService.writeErrorToMemoryHistory(memoryConfig, null, input, e);
                        }
                    });
                }
            }, actionListener::onFailure);
            memoryContainerHelper.indexData(memoryConfig, indexRequest, responseActionListener);
        } catch (Exception e) {
            log.error("Failed to add memory", e);
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private IndexRequest createWorkingMemoryRequest(String workingMemoryIndex, MLAddMemoriesInput mlAddMemoriesInput) {
        IndexRequest indexRequest = new IndexRequest(workingMemoryIndex);

        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            // Add memory content from input object
            mlAddMemoriesInput.toXContent(builder, ToXContent.EMPTY_PARAMS, true);

            indexRequest.source(builder);
            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            return indexRequest;
        } catch (IOException e) {
            log.error("Failed to build index request source", e);
            throw new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void extractLongTermMemory(
        MLAddMemoriesInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<MLAddMemoriesResponse> actionListener
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
                        // Preserve client errors (4XX) with their detailed messages
                        if (e instanceof OpenSearchStatusException) {
                            OpenSearchStatusException osException = (OpenSearchStatusException) e;
                            if (osException.status().getStatus() >= 400 && osException.status().getStatus() < 500) {
                                actionListener.onFailure(e);
                                return;
                            }
                        }
                        // Wrap server errors and unexpected exceptions
                        log.error("Failed to extract facts with LLM", e);
                        memoryOperationsService.writeErrorToMemoryHistory(memoryConfig, strategyNameSpace, input, e);
                        actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
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
        MLAddMemoriesInput input,
        List<MessageInput> messages,
        User user,
        List<String> facts,
        MemoryConfiguration memoryConfig,
        ActionListener<MLAddMemoriesResponse> actionListener
    ) {
        List<IndexRequest> indexRequests = new ArrayList<>();
        List<MemoryInfo> memoryInfos = new ArrayList<>();

        // Search for similar facts and make memory decisions
        if (!facts.isEmpty() && memoryConfig != null && memoryConfig.getLlmId() != null) {
            log.debug("Searching for similar facts in session to make memory decisions");
            memorySearchService.searchSimilarFactsForSession(strategy, input, facts, memoryConfig, ActionListener.wrap(allSearchResults -> {
                log.debug("Found {} total similar facts across all {} new facts", allSearchResults.size(), facts.size());

                if (allSearchResults.size() > 0) {
                    memoryProcessingService
                        .makeMemoryDecisions(facts, allSearchResults, strategy, memoryConfig, ActionListener.wrap(decisions -> {
                            memoryOperationsService
                                .executeMemoryOperations(
                                    decisions,
                                    memoryConfig,
                                    strategyNameSpace,
                                    user,
                                    input,
                                    strategy,
                                    ActionListener.wrap(operationResults -> {
                                        List<MemoryResult> allResults = new ArrayList<>(operationResults);
                                        MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(allResults).build();
                                        actionListener.onResponse(response);
                                    }, actionListener::onFailure)
                                );
                        }, e -> {
                            // Preserve client errors (4XX) with their detailed messages
                            if (e instanceof OpenSearchStatusException) {
                                OpenSearchStatusException osException = (OpenSearchStatusException) e;
                                if (osException.status().getStatus() >= 400 && osException.status().getStatus() < 500) {
                                    actionListener.onFailure(e);
                                    return;
                                }
                            }
                            // Wrap server errors and unexpected exceptions
                            log.error("Failed to make memory decisions", e);
                            actionListener
                                .onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
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
                            strategy,
                            ActionListener.wrap(operationResults -> {
                                List<MemoryResult> allResults = new ArrayList<>(operationResults);
                                MLAddMemoriesResponse response = MLAddMemoriesResponse.builder().results(allResults).build();
                                actionListener.onResponse(response);
                            }, actionListener::onFailure)
                        );
                }
            }, e -> {
                log.error("Failed to search similar facts", e);
                actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
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
                    strategy,
                    indexRequests,
                    memoryInfos,
                    input.getMemoryContainerId()
                );
        }
    }

}
