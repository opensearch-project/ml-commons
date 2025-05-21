/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRegisterAction;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRegisterOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.BaseMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpToolsRegisterAction extends HandledTransportAction<ActionRequest, MLMcpToolsRegisterNodesResponse> {

    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    private volatile boolean mcpServerEnabled;
    private final MLIndicesHandler mlIndicesHandler;
    private final McpToolsHelper mcpToolsHelper;

    @Inject
    public TransportMcpToolsRegisterAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLIndicesHandler mlIndicesHandler,
        McpToolsHelper mcpToolsHelper
    ) {
        super(MLMcpToolsRegisterAction.NAME, transportService, actionFilters, MLMcpToolsRegisterNodesRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mcpToolsHelper = mcpToolsHelper;
        mcpServerEnabled = ML_COMMONS_MCP_SERVER_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_SERVER_ENABLED, it -> mcpServerEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsRegisterNodesResponse> listener) {
        if (!mcpServerEnabled) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        MLMcpToolsRegisterNodesRequest registerNodesRequest = (MLMcpToolsRegisterNodesRequest) request;
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRegisterNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<Boolean> initIndexListener = ActionListener.wrap(created -> {
                ActionListener<List<RegisterMcpTool>> searchResultListener = ActionListener.wrap(searchResult -> {
                    if (!searchResult.isEmpty()) {
                        Set<String> registerToolNames = registerNodesRequest
                            .getMcpTools()
                            .stream()
                            .map(RegisterMcpTool::getName)
                            .collect(Collectors.toSet());
                        List<String> existingTools = searchResult
                            .stream()
                            .map(RegisterMcpTool::getName)
                            .filter(registerToolNames::contains)
                            .toList();
                        String exceptionMessage = String
                            .format(Locale.ROOT, "Unable to register tools: %s as they already exist", existingTools);
                        log.warn(exceptionMessage);
                        restoreListener.onFailure(new OpenSearchException(exceptionMessage));
                    } else {
                        indexMcpTools(registerNodesRequest, restoreListener);
                    }
                }, e -> {
                    log.error("Failed to search mcp tools index", e);
                    restoreListener.onFailure(e);
                });
                mcpToolsHelper
                    .searchToolsWithParsedResult(
                        registerNodesRequest.getMcpTools().stream().map(RegisterMcpTool::getName).toList(),
                        searchResultListener
                    );
            }, e -> {
                log.error("Failed to create .plugins-ml-mcp-tools index", e);
                restoreListener.onFailure(e);
            });
            mlIndicesHandler.initMLMcpToolsIndex(initIndexListener);
        } catch (Exception e) {
            log.error("Failed to register mcp tools", e);
            listener.onFailure(e);
        }
    }

    private void indexMcpTools(
        MLMcpToolsRegisterNodesRequest registerNodesRequest,
        ActionListener<MLMcpToolsRegisterNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRegisterNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<BulkResponse> indexResultListener = ActionListener.wrap(bulkResponse -> {
                if (!bulkResponse.hasFailures()) {
                    // documents indexing successfully, merge existing tools and new tools to form a fullTools, when a node doesn't have any
                    // tool registered,
                    // register full tools, this way any node at any time it only have either non or all tools in it.
                    // This also addressed an edge case that a node doesn't have any tool registered and received a register tool request,
                    // it'll be registered
                    // with new tools only, when SSE connection request comes, it tries to load all tools, and then it'll have tool already
                    // exist in MCP server issue.
                    registerMcpToolsOnNodes(
                        new StringBuilder(),
                        updateVersion(registerNodesRequest, bulkResponse),
                        registerNodesRequest.getMcpTools().stream().map(RegisterMcpTool::getName).collect(Collectors.toUnmodifiableSet()),
                        restoreListener
                    );
                } else {
                    AtomicReference<Set<String>> indexSucceedTools = new AtomicReference<>();
                    indexSucceedTools.set(new HashSet<>());
                    AtomicReference<Map<String, String>> indexFailedTools = new AtomicReference<>();
                    indexFailedTools.set(new HashMap<>());
                    Arrays.stream(bulkResponse.getItems()).forEach(y -> {
                        if (y.isFailed()) {
                            indexFailedTools.get().put(y.getId(), y.getFailure().getMessage());
                            registerNodesRequest.getMcpTools().removeIf(x -> x.getName().equals(y.getId()));
                        } else {
                            indexSucceedTools.get().add(y.getId());
                        }
                    });
                    StringBuilder errMsgBuilder = new StringBuilder();
                    for (Map.Entry<String, String> indexFailedTool : indexFailedTools.get().entrySet()) {
                        errMsgBuilder
                            .append(
                                String
                                    .format(
                                        Locale.ROOT,
                                        "Failed to persist mcp tool: %s into system index with error: %s",
                                        indexFailedTool.getKey(),
                                        indexFailedTool.getValue()
                                    )
                            );
                        errMsgBuilder.append("\n");
                    }
                    log.error(errMsgBuilder.toString());
                    if (!indexSucceedTools.get().isEmpty()) {
                        registerMcpToolsOnNodes(
                            errMsgBuilder,
                            updateVersion(registerNodesRequest, bulkResponse),
                            indexSucceedTools.get(),
                            restoreListener
                        );
                    } else {
                        restoreListener
                            .onFailure(new OpenSearchException(errMsgBuilder.deleteCharAt(errMsgBuilder.length() - 1).toString()));
                    }
                }
            }, e -> {
                log.error("Failed to persist mcp tools into system index because exception: {}", e.getMessage());
                restoreListener.onFailure(e);
            });

            BulkRequest bulkRequest = new BulkRequest();
            for (RegisterMcpTool mcpTool : registerNodesRequest.getMcpTools()) {
                IndexRequest indexRequest = new IndexRequest(MLIndex.MCP_TOOLS.getIndexName());
                // Set opType to create to avoid race condition when creating tools with same name.
                indexRequest.opType(DocWriteRequest.OpType.CREATE);
                indexRequest.id(mcpTool.getName());
                Map<String, Object> source = new HashMap<>();
                source.put(BaseMcpTool.NAME_FIELD, mcpTool.getName());
                source.put(BaseMcpTool.TYPE_FIELD, mcpTool.getType());
                source.put(BaseMcpTool.PARAMS_FIELD, mcpTool.getParameters());
                source.put(BaseMcpTool.ATTRIBUTES_FIELD, mcpTool.getAttributes());
                source.put(BaseMcpTool.DESCRIPTION_FIELD, mcpTool.getDescription());
                source.put(CommonValue.CREATE_TIME_FIELD, Instant.now().toEpochMilli());
                indexRequest.source(source);
                bulkRequest.add(indexRequest);
            }
            client.bulk(bulkRequest, indexResultListener);
        } catch (Exception e) {
            log.error("Failed to register mcp tools", e);
            listener.onFailure(e);
        }
    }

    private MLMcpToolsRegisterNodesRequest updateVersion(MLMcpToolsRegisterNodesRequest registerNodesRequest, BulkResponse bulkResponse) {
        Map<String, Long> version = Arrays
            .stream(bulkResponse.getItems())
            .filter(x -> !x.isFailed())
            .collect(Collectors.toMap(BulkItemResponse::getId, x -> x.getResponse().getVersion()));
        registerNodesRequest.getMcpTools().forEach(x -> x.setVersion(version.get(x.getName())));
        return registerNodesRequest;
    }

    private void registerMcpToolsOnNodes(
        StringBuilder errMsgBuilder,
        MLMcpToolsRegisterNodesRequest registerNodesRequest,
        Set<String> indexSucceedTools,
        ActionListener<MLMcpToolsRegisterNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRegisterNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<MLMcpToolsRegisterNodesResponse> addToMemoryResultListener = ActionListener.wrap(r -> {
                if (r.failures() != null && !r.failures().isEmpty()) {
                    r.failures().forEach(x -> {
                        errMsgBuilder
                            .append(
                                String
                                    .format(
                                        Locale.ROOT,
                                        "Tools: %s are persisted successfully but failed to register to mcp server memory with error: %s",
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
                                "Tools are persisted successfully but failed to register to mcp server memory with error: %s",
                                e.getMessage()
                            )
                    );
                log.error(errMsgBuilder.toString(), e);
                restoreListener.onFailure(new OpenSearchException(errMsgBuilder.toString()));
            });
            client.execute(MLMcpToolsRegisterOnNodesAction.INSTANCE, registerNodesRequest, addToMemoryResultListener);
        } catch (Exception e) {
            log.error("Failed to register mcp tools on nodes", e);
            listener.onFailure(e);
        }
    }
}
