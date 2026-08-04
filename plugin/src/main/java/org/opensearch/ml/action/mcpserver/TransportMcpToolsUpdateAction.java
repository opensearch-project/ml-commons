/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsUpdateAction;
import org.opensearch.ml.common.transport.mcpserver.requests.McpToolBaseInput;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.McpToolUpdateInput;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodeResponse;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpToolsUpdateAction extends HandledTransportAction<ActionRequest, MLMcpToolsUpdateNodesResponse> {

    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final McpToolsHelper mcpToolsHelper;

    @Inject
    public TransportMcpToolsUpdateAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        McpToolsHelper mcpToolsHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMcpToolsUpdateAction.NAME, transportService, actionFilters, MLMcpToolsUpdateNodesRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mcpToolsHelper = mcpToolsHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsUpdateNodesResponse> listener) {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        if (!clusterService.state().metadata().hasIndex(MLIndex.MCP_TOOLS.getIndexName())) {
            listener.onFailure(new OpenSearchException("MCP tools index doesn't exist"));
            return;
        }
        MLMcpToolsUpdateNodesRequest updateNodesRequest = (MLMcpToolsUpdateNodesRequest) request;
        Set<String> updateToolSet = new HashSet<>();
        updateNodesRequest.getMcpTools().forEach(x -> updateToolSet.add(x.getName()));
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsUpdateNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<SearchResponse> searchResultListener = ActionListener.wrap(searchResult -> {
                if (Objects.requireNonNull(searchResult.getHits().getHits()).length > 0) {
                    List<SearchedMcpToolWrapper> searchedMcpToolWrappers = new ArrayList<>();
                    Arrays.stream(Objects.requireNonNull(searchResult.getHits().getHits())).forEach(x -> {
                        try (
                            XContentParser parser = jsonXContent
                                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, x.getSourceAsString())
                        ) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            McpToolRegisterInput registerMcpTool = McpToolRegisterInput.parse(parser);
                            updateToolSet.remove(registerMcpTool.getName());
                            SearchedMcpToolWrapper updateMcpToolWrapper = new SearchedMcpToolWrapper.SearchedMcpToolWrapperBuilder()
                                .seqNo(x.getSeqNo())
                                .primaryTerm(x.getPrimaryTerm())
                                .mcpTool(registerMcpTool)
                                .build();
                            searchedMcpToolWrappers.add(updateMcpToolWrapper);
                        } catch (IOException e) {
                            log.error("Failed to parse mcp tools configuration");
                            restoreListener.onFailure(e);
                        }
                    });
                    // If any to update tool not found, return error.
                    if (!updateToolSet.isEmpty()) {
                        log.warn("Failed to find tools: {} in system index", updateToolSet);
                        restoreListener.onFailure(new OpenSearchException("Failed to find one or more requested tools in system index"));
                    } else {
                        updateMcpTools(updateNodesRequest, searchedMcpToolWrappers, restoreListener);
                    }
                } else {
                    restoreListener.onFailure(new OpenSearchException("Failed to update tools as none of them is found in index"));
                }
            }, e -> {
                log.error("Failed to search mcp tools index", e);
                restoreListener.onFailure(e);
            });
            mcpToolsHelper
                .searchToolsWithPrimaryTermAndSeqNo(
                    updateNodesRequest.getMcpTools().stream().map(McpToolUpdateInput::getName).toList(),
                    searchResultListener
                );
        } catch (Exception e) {
            log.error("Failed to update mcp tools", e);
            listener.onFailure(e);
        }
    }

    private void updateMcpTools(
        MLMcpToolsUpdateNodesRequest updateNodesRequest,
        List<SearchedMcpToolWrapper> searchedMcpToolWrappers,
        ActionListener<MLMcpToolsUpdateNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsUpdateNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<BulkResponse> updateResultListener = ActionListener.wrap(bulkResponse -> {
                if (!bulkResponse.hasFailures()) {
                    restoreListener
                        .onResponse(
                            new MLMcpToolsUpdateNodesResponse(
                                clusterService.getClusterName(),
                                List.of(new MLMcpToolsUpdateNodeResponse(clusterService.localNode(), true)),
                                List.of()
                            )
                        );
                } else {
                    AtomicReference<Set<String>> updateSucceedTools = new AtomicReference<>();
                    updateSucceedTools.set(new HashSet<>());
                    AtomicReference<Map<String, String>> updateFailedTools = new AtomicReference<>();
                    updateFailedTools.set(new HashMap<>());
                    Arrays.stream(bulkResponse.getItems()).forEach(y -> {
                        if (y.isFailed()) {
                            updateFailedTools.get().put(y.getId(), y.getFailure().getMessage());
                            updateNodesRequest.getMcpTools().removeIf(x -> x.getName().equals(y.getId()));
                            searchedMcpToolWrappers.removeIf(x -> x.getMcpTool().getName().equals(y.getId()));
                        } else {
                            updateSucceedTools.get().add(y.getId());
                        }
                    });
                    StringBuilder errMsgBuilder = new StringBuilder();
                    for (Map.Entry<String, String> indexFailedTool : updateFailedTools.get().entrySet()) {
                        errMsgBuilder
                            .append(
                                String
                                    .format(
                                        Locale.ROOT,
                                        "Failed to update mcp tool: %s in system index with error: %s",
                                        indexFailedTool.getKey(),
                                        indexFailedTool.getValue()
                                    )
                            );
                        errMsgBuilder.append("\n");
                    }
                    log.error(errMsgBuilder.toString());
                    StringBuilder responseErrorBuilder = new StringBuilder(
                        String.format(Locale.ROOT, "Failed to update %d tool(s) in system index", updateFailedTools.get().size())
                    );
                    restoreListener.onFailure(new OpenSearchException(responseErrorBuilder.toString()));
                }
            }, e -> {
                log.error("Failed to update mcp tools in system index because exception: {}", e.getMessage());
                restoreListener.onFailure(e);
            });

            Map<String, SearchedMcpToolWrapper> searchedMcpToolWrapperMap = searchedMcpToolWrappers
                .stream()
                .collect(Collectors.toMap(x -> x.getMcpTool().getName(), x -> x));
            BulkRequest bulkRequest = new BulkRequest();
            for (McpToolUpdateInput mcpTool : updateNodesRequest.getMcpTools()) {
                UpdateRequest updateRequest = new UpdateRequest(MLIndex.MCP_TOOLS.getIndexName(), mcpTool.getName());
                updateRequest.setIfSeqNo(searchedMcpToolWrapperMap.get(mcpTool.getName()).getSeqNo());
                updateRequest.setIfPrimaryTerm(searchedMcpToolWrapperMap.get(mcpTool.getName()).getPrimaryTerm());
                Map<String, Object> source = new HashMap<>();
                if (mcpTool.getDescription() != null)
                    source.put(McpToolBaseInput.DESCRIPTION_FIELD, mcpTool.getDescription());
                if (mcpTool.getParameters() != null)
                    source.put(McpToolBaseInput.PARAMS_FIELD, mcpTool.getParameters());
                if (mcpTool.getAttributes() != null)
                    source.put(McpToolBaseInput.ATTRIBUTES_FIELD, mcpTool.getAttributes());
                source.put(CommonValue.LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
                updateRequest.doc(source);
                bulkRequest.add(updateRequest);
            }
            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            client.bulk(bulkRequest, updateResultListener);
        } catch (Exception e) {
            log.error("Failed to update mcp tools", e);
            listener.onFailure(e);
        }
    }

    @Builder
    @Data
    private static class SearchedMcpToolWrapper {
        private McpToolRegisterInput mcpTool;
        private Long primaryTerm;
        private Long seqNo;
        private Long version;
    }
}
