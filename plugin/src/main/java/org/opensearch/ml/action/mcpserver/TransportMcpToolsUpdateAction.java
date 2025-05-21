/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

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

import com.google.common.collect.ImmutableMap;
import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkItemResponse;
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
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsUpdateAction;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsUpdateOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.BaseMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.update.MLMcpToolsUpdateNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.update.UpdateMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;
import org.opensearch.ml.common.utils.StringUtils;
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
    private volatile boolean mcpServerEnabled;
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
        McpToolsHelper mcpToolsHelper
    ) {
        super(MLMcpToolsUpdateAction.NAME, transportService, actionFilters, MLMcpToolsUpdateNodesRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mcpToolsHelper = mcpToolsHelper;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsUpdateNodesResponse> listener) {
        if (!mcpServerEnabled) {
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
                                .createParser(
                                    NamedXContentRegistry.EMPTY,
                                    LoggingDeprecationHandler.INSTANCE,
                                    x.getSourceAsString()
                                )
                        ) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            RegisterMcpTool registerMcpTool = RegisterMcpTool.parse(parser);
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
                        String errMsg = String.format("Failed to find tools: %s in system index", updateToolSet);
                        log.warn(errMsg);
                        restoreListener.onFailure(new OpenSearchException(errMsg));
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
                .searchToolsForUpdate(updateNodesRequest.getMcpTools().stream().map(UpdateMcpTool::getName).toList(), searchResultListener);
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
                    // documents indexing successfully, merge existing tools and new tools to form a fullTools, when a node doesn't have any
                    // tool registered,
                    // update full tools, this way any node at any time it only have either non or all tools in it.
                    // This also addressed an edge case that a node doesn't have any tool registered and received a update tool request,
                    // it'll be registered
                    // with new tools only, when SSE connection request comes, it tries to load all tools, and then it'll have tool already
                    // exist in MCP server issue.
                    updateMcpToolsOnNodes(
                        new StringBuilder(),
                        mergeDocFields(updateNodesRequest, searchedMcpToolWrappers, bulkResponse),
                        updateNodesRequest.getMcpTools().stream().map(UpdateMcpTool::getName).collect(Collectors.toUnmodifiableSet()),
                        restoreListener
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
                    if (!updateSucceedTools.get().isEmpty()) {
                        updateMcpToolsOnNodes(
                            errMsgBuilder,
                            mergeDocFields(updateNodesRequest, searchedMcpToolWrappers, bulkResponse),
                            updateSucceedTools.get(),
                            restoreListener
                        );
                    } else {
                        restoreListener
                            .onFailure(new OpenSearchException(errMsgBuilder.deleteCharAt(errMsgBuilder.length() - 1).toString()));
                    }
                }
            }, e -> {
                log.error("Failed to update mcp tools in system index because exception: {}", e.getMessage());
                restoreListener.onFailure(e);
            });

            Map<String, SearchedMcpToolWrapper> searchedMcpToolWrapperMap = searchedMcpToolWrappers
                .stream()
                .collect(Collectors.toMap(x -> x.getMcpTool().getName(), x -> x));
            BulkRequest bulkRequest = new BulkRequest();
            for (UpdateMcpTool mcpTool : updateNodesRequest.getMcpTools()) {
                UpdateRequest updateRequest = new UpdateRequest(MLIndex.MCP_TOOLS.getIndexName(), mcpTool.getName());
                updateRequest.setIfSeqNo(searchedMcpToolWrapperMap.get(mcpTool.getName()).getSeqNo());
                updateRequest.setIfPrimaryTerm(searchedMcpToolWrapperMap.get(mcpTool.getName()).getPrimaryTerm());
                Map<String, Object> source = new HashMap<>();
                if (mcpTool.getDescription() != null) source.put(BaseMcpTool.DESCRIPTION_FIELD, mcpTool.getDescription());
                if (mcpTool.getParameters() != null) source.put(BaseMcpTool.PARAMS_FIELD, mcpTool.getParameters());
                if (mcpTool.getAttributes() != null) source.put(BaseMcpTool.ATTRIBUTES_FIELD, mcpTool.getAttributes());
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

    private MLMcpToolsUpdateNodesRequest mergeDocFields(
        MLMcpToolsUpdateNodesRequest updateNodesRequest,
        List<SearchedMcpToolWrapper> updateMcpToolWrappers,
        BulkResponse bulkResponse
    ) {
        Map<String, RegisterMcpTool> mcpToolsMap = updateMcpToolWrappers
            .stream()
            .collect(Collectors.toMap(x -> x.getMcpTool().getName(), SearchedMcpToolWrapper::getMcpTool));
        Map<String, Long> versions = Arrays
            .stream(bulkResponse.getItems())
            .filter(x -> !x.isFailed())
            .collect(Collectors.toMap(BulkItemResponse::getId, x -> x.getResponse().getVersion()));
        updateNodesRequest.getMcpTools().forEach(x -> {
            RegisterMcpTool registerMcpTool = mcpToolsMap.get(x.getName());
            x.setType(registerMcpTool.getType());
            if (x.getAttributes() == null) {
                x.setAttributes(registerMcpTool.getAttributes());
            }
            if (x.getParameters() == null) {
                x.setParameters(registerMcpTool.getParameters());
            }
            if (x.getDescription() == null) {
                x.setDescription(registerMcpTool.getDescription());
            }
            x.setVersion(versions.get(x.getName()));
        });
        return updateNodesRequest;
    }

    private void updateMcpToolsOnNodes(
        StringBuilder errMsgBuilder,
        MLMcpToolsUpdateNodesRequest registerNodesRequest,
        Set<String> indexSucceedTools,
        ActionListener<MLMcpToolsUpdateNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsUpdateNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<MLMcpToolsUpdateNodesResponse> addToMemoryResultListener = ActionListener.wrap(r -> {
                if (r.failures() != null && !r.failures().isEmpty()) {
                    r.failures().forEach(x -> {
                        errMsgBuilder
                            .append(
                                String
                                    .format(
                                        Locale.ROOT,
                                        "Tools: %s are updated successfully but failed to update to mcp server memory with error: %s",
                                        indexSucceedTools,
                                        x.getRootCause().getMessage()
                                    )
                            );
                        errMsgBuilder.append("\n");
                    });
                    errMsgBuilder.deleteCharAt(errMsgBuilder.length() - 1);
                    log.error(errMsgBuilder.toString());
                    restoreListener.onFailure(new OpenSearchException(errMsgBuilder.toString()));
                } else {
                    restoreListener.onResponse(r);
                }
            }, e -> {
                errMsgBuilder
                    .append(
                        String
                            .format(
                                Locale.ROOT,
                                "Tools are updated successfully but failed to update to mcp server memory with error: %s",
                                e.getMessage()
                            )
                    );
                log.error(errMsgBuilder.toString(), e);
                restoreListener.onFailure(new OpenSearchException(errMsgBuilder.toString()));
            });
            client.execute(MLMcpToolsUpdateOnNodesAction.INSTANCE, registerNodesRequest, addToMemoryResultListener);
        } catch (Exception e) {
            log.error("Failed to update mcp tools on nodes", e);
            listener.onFailure(e);
        }
    }

    @Builder
    @Data
    private static class SearchedMcpToolWrapper {
        private RegisterMcpTool mcpTool;
        private Long primaryTerm;
        private Long seqNo;
        private Long version;
    }
}
