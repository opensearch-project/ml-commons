/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRemoveAction;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpToolsRemoveOnNodesAction;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.transport.mcpserver.requests.remove.MLMcpToolsRemoveNodesRequest;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpToolsRemoveNodesResponse;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportMcpToolsRemoveAction extends HandledTransportAction<ActionRequest, MLMcpToolsRemoveNodesResponse> {

    TransportService transportService;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final McpToolsHelper mcpToolsHelper;

    @Inject
    public TransportMcpToolsRemoveAction(
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
        super(MLMcpToolsRemoveAction.NAME, transportService, actionFilters, MLMcpMessageRequest::new);
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
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMcpToolsRemoveNodesResponse> listener) {
        if (!mlFeatureEnabledSetting.isMcpServerEnabled()) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE));
            return;
        }
        MLMcpToolsRemoveNodesRequest removeToolsOnNodesRequest = (MLMcpToolsRemoveNodesRequest) request;
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRemoveNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<List<McpToolRegisterInput>> searchResultListener = ActionListener.wrap(searchResult -> {
                if (!searchResult.isEmpty()) {
                    // Tools search found results.
                    List<String> foundTools = searchResult.stream().map(McpToolRegisterInput::getName).toList();
                    foundTools.forEach(x -> removeToolsOnNodesRequest.getMcpTools().remove(x));
                    if (!removeToolsOnNodesRequest.getMcpTools().isEmpty()) {
                        // There are tools not found, do not proceed.
                        String exceptionMessage = String
                            .format(
                                Locale.ROOT,
                                "Unable to remove tools as these tools: %s are not found in system index",
                                removeToolsOnNodesRequest.getMcpTools()
                            );
                        log.info(exceptionMessage);
                        restoreListener.onFailure(new OpenSearchException(exceptionMessage));
                    } else {
                        bulkDeleteMcpTools(removeToolsOnNodesRequest, foundTools, restoreListener);
                    }
                } else {
                    // No results found searching tools, do not proceed.
                    String exceptionMessage = "Unable to remove tools as no tool in the request found in system index";
                    log.warn(exceptionMessage);
                    restoreListener.onFailure(new OpenSearchException(exceptionMessage));
                }
            }, e -> {
                log.error("Failed to search mcp tools index", e);
                restoreListener.onFailure(e);
            });
            mcpToolsHelper.searchToolsWithVersion(removeToolsOnNodesRequest.getMcpTools(), searchResultListener);
        } catch (Exception e) {
            log.error("Failed to remove mcp tools caused by system internal error", e);
            listener.onFailure(e);
        }
    }

    private void bulkDeleteMcpTools(
        MLMcpToolsRemoveNodesRequest removeToolsOnNodesRequest,
        List<String> foundTools,
        ActionListener<MLMcpToolsRemoveNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRemoveNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            // All tools to remove are found in MCP server, proceeding to remove from index.
            ActionListener<BulkResponse> bulkResultListener = ActionListener.wrap(bulkResponse -> {
                if (!bulkResponse.hasFailures()) {
                    // documents deleting successfully, starting to remove tools in MCP memory.
                    removeMcpToolsInMemory(removeToolsOnNodesRequest, new StringBuilder(), foundTools, restoreListener);
                } else {
                    // remove only the successfully deleted tools in memory, the failure on memory operation won't have impact on
                    // consistency.
                    StringBuilder errMsgBuilder = new StringBuilder();
                    List<String> removeSucceedTools = new ArrayList<>();
                    Arrays.stream(bulkResponse.getItems()).forEach(x -> {
                        if (x.isFailed()) {
                            errMsgBuilder
                                .append(
                                    String
                                        .format(
                                            Locale.ROOT,
                                            "Failed to remove tool: %s from index with error: %s",
                                            x.getId(),
                                            x.getFailure().getMessage()
                                        )
                                );
                            errMsgBuilder.append("\n");
                        } else {
                            removeSucceedTools.add(x.getId());
                        }
                    });

                    removeToolsOnNodesRequest.setMcpTools(removeSucceedTools);
                    removeMcpToolsInMemory(removeToolsOnNodesRequest, errMsgBuilder, removeSucceedTools, restoreListener);
                }
            }, e -> {
                log.error("Failed to delete MCP tools in index", e);
                restoreListener.onFailure(e);
            });
            BulkRequest bulkRequest = new BulkRequest();
            for (String name : foundTools) {
                DeleteRequest deleteRequest = new DeleteRequest(MLIndex.MCP_TOOLS.getIndexName(), name);
                bulkRequest.add(deleteRequest);
            }
            client.bulk(bulkRequest, bulkResultListener);
        } catch (Exception e) {
            log.error("Failed to remove mcp tools", e);
            listener.onFailure(e);
        }
    }

    private void removeMcpToolsInMemory(
        MLMcpToolsRemoveNodesRequest removeToolsOnNodesRequest,
        StringBuilder errMsgBuilder,
        List<String> removeSucceedTools,
        ActionListener<MLMcpToolsRemoveNodesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMcpToolsRemoveNodesResponse> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<MLMcpToolsRemoveNodesResponse> removeFromMemoryResultListener = ActionListener.wrap(r -> {
                if (r.failures() != null && !r.failures().isEmpty()) {
                    r.failures().forEach(x -> {

                        errMsgBuilder
                            .append(
                                String
                                    .format(
                                        Locale.ROOT,
                                        "Tools: %s are removed successfully in index but failed to remove from mcp server in memory with error: %s",
                                        removeSucceedTools,
                                        x.getRootCause().getMessage()
                                    )
                            );
                        errMsgBuilder.append("\n");
                    });
                    errMsgBuilder.deleteCharAt(errMsgBuilder.length() - 1);

                    log.error(errMsgBuilder.toString());
                    restoreListener.onFailure(new OpenSearchException(errMsgBuilder.toString()));
                } else {
                    if (errMsgBuilder.isEmpty()) {
                        restoreListener.onResponse(r);
                    } else {
                        restoreListener
                            .onFailure(new OpenSearchException(errMsgBuilder.deleteCharAt(errMsgBuilder.length() - 1).toString()));
                    }
                }
            }, e -> {

                errMsgBuilder
                    .append(
                        String
                            .format(
                                Locale.ROOT,
                                "Tools are removed successfully in index but failed to remove from mcp server memory with error: %s",
                                e.getMessage()
                            )
                    );

                log.error(errMsgBuilder.toString(), e);
                restoreListener.onFailure(new OpenSearchException(errMsgBuilder.toString()));
            });
            removeToolsOnNodesRequest.setMcpTools(removeSucceedTools);
            client.execute(MLMcpToolsRemoveOnNodesAction.INSTANCE, removeToolsOnNodesRequest, removeFromMemoryResultListener);
        } catch (Exception e) {
            String errMsg = String.format(Locale.ROOT, "Failed to remove mcp tools on nodes memory with error: %s", e.getMessage());
            log.error(errMsg, e);
            listener.onFailure(new OpenSearchException(errMsg));
        }
    }
}
