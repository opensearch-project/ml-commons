/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ORPHAN_SWEEP_BASELINE_TIME_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunResponse;
import org.opensearch.ml.common.transport.memorycontainer.MemoryRetentionDryRunResult;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Transport action for the retention dry-run: loads the target container(s), enforces access control, then
 * delegates the read-only "what would be deleted" computation to {@link MemoryRetentionJobProcessor}. Performs
 * no deletions.
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportMemoryRetentionDryRunAction extends HandledTransportAction<ActionRequest, MLMemoryRetentionDryRunResponse> {

    private static final int CONTAINER_PAGE_SIZE = 100;

    final Client client;
    final ClusterService clusterService;
    final ThreadPool threadPool;
    final NamedXContentRegistry xContentRegistry;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportMemoryRetentionDryRunAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLMemoryRetentionDryRunAction.NAME, transportService, actionFilters, MLMemoryRetentionDryRunRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLMemoryRetentionDryRunResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLMemoryRetentionDryRunRequest dryRunRequest = MLMemoryRetentionDryRunRequest.fromActionRequest(request);
        String tenantId = dryRunRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        MemoryRetentionJobProcessor processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);

        if (dryRunRequest.isClusterWide()) {
            executeClusterWide(processor, user, tenantId, actionListener);
        } else {
            executeSingle(processor, user, dryRunRequest.getMemoryContainerId(), tenantId, actionListener);
        }
    }

    private void executeSingle(
        MemoryRetentionJobProcessor processor,
        User user,
        String containerId,
        String tenantId,
        ActionListener<MLMemoryRetentionDryRunResponse> actionListener
    ) {
        GetRequest getRequest = new GetRequest(ML_MEMORY_CONTAINER_INDEX, containerId);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryRetentionDryRunResponse> wrapped = ActionListener.runBefore(actionListener, context::restore);
            client.get(getRequest, ActionListener.wrap(getResponse -> {
                if (getResponse == null || !getResponse.isExists()) {
                    wrapped
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find memory container with the provided memory container id: " + containerId,
                                RestStatus.NOT_FOUND
                            )
                        );
                    return;
                }
                MLMemoryContainer container = parseContainer(getResponse.getSourceAsString());
                if (!TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, container.getTenantId(), wrapped)) {
                    return;
                }
                if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                    wrapped
                        .onFailure(
                            new OpenSearchStatusException(
                                "User doesn't have privilege to perform this operation on this memory container",
                                RestStatus.FORBIDDEN
                            )
                        );
                    return;
                }
                Long baseline = extractOrphanBaseline(getResponse.getSourceAsMap());
                processor
                    .dryRunContainer(
                        container.getConfiguration(),
                        containerId,
                        baseline,
                        ActionListener
                            .wrap(
                                result -> wrapped.onResponse(new MLMemoryRetentionDryRunResponse(List.of(result), false)),
                                wrapped::onFailure
                            )
                    );
            }, e -> {
                if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                    wrapped.onFailure(new OpenSearchStatusException("Failed to find memory container index", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get memory container {} for retention dry-run", containerId, e);
                    wrapped.onFailure(RestActionUtils.wrapAsStatusException(e));
                }
            }));
        } catch (Exception e) {
            log.error("Failed to run retention dry-run for container {}", containerId, e);
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void executeClusterWide(
        MemoryRetentionJobProcessor processor,
        User user,
        String tenantId,
        ActionListener<MLMemoryRetentionDryRunResponse> actionListener
    ) {
        List<MemoryRetentionDryRunResult> results = new ArrayList<>();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryRetentionDryRunResponse> wrapped = ActionListener.runBefore(actionListener, context::restore);
            searchContainerPage(processor, user, tenantId, null, results, wrapped);
        } catch (Exception e) {
            log.error("Failed to run cluster-wide retention dry-run", e);
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void searchContainerPage(
        MemoryRetentionJobProcessor processor,
        User user,
        String tenantId,
        Object[] searchAfter,
        List<MemoryRetentionDryRunResult> results,
        ActionListener<MLMemoryRetentionDryRunResponse> listener
    ) {
        SearchRequest request = new SearchRequest(ML_MEMORY_CONTAINER_INDEX);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .size(CONTAINER_PAGE_SIZE)
            .sort("_id", SortOrder.ASC)
            .fetchSource(true);
        if (searchAfter != null) {
            source.searchAfter(searchAfter);
        }
        request.source(source);

        client.search(request, ActionListener.wrap(response -> {
            SearchHit[] hits = response.getHits().getHits();
            if (hits.length == 0) {
                listener.onResponse(new MLMemoryRetentionDryRunResponse(results, true));
                return;
            }
            Object[] nextPageSort = hits.length == CONTAINER_PAGE_SIZE ? hits[hits.length - 1].getSortValues() : null;
            processHitChain(processor, user, tenantId, hits, 0, nextPageSort, results, listener);
        }, e -> {
            if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                // No container index yet: nothing to evaluate, return an empty array rather than an error.
                listener.onResponse(new MLMemoryRetentionDryRunResponse(results, true));
            } else {
                log.error("Failed to search memory containers for cluster-wide retention dry-run", e);
                listener.onFailure(RestActionUtils.wrapAsStatusException(e));
            }
        }));
    }

    private void processHitChain(
        MemoryRetentionJobProcessor processor,
        User user,
        String tenantId,
        SearchHit[] hits,
        int index,
        Object[] nextPageSort,
        List<MemoryRetentionDryRunResult> results,
        ActionListener<MLMemoryRetentionDryRunResponse> listener
    ) {
        if (index >= hits.length) {
            if (nextPageSort != null) {
                searchContainerPage(processor, user, tenantId, nextPageSort, results, listener);
            } else {
                listener.onResponse(new MLMemoryRetentionDryRunResponse(results, true));
            }
            return;
        }

        SearchHit hit = hits[index];
        String containerId = hit.getId();
        final MLMemoryContainer container;
        try {
            container = parseContainer(hit.getSourceAsString());
        } catch (Exception e) {
            log.warn("Skipping container {} in retention dry-run: failed to parse", containerId, e);
            processHitChain(processor, user, tenantId, hits, index + 1, nextPageSort, results, listener);
            return;
        }

        // Tenant + access filtering: silently skip containers the caller cannot see, mirroring the job's own
        // per-container isolation. A mismatched tenant or denied access simply excludes the container.
        boolean tenantOk = tenantId == null || container.getTenantId() == null || tenantId.equals(container.getTenantId());
        if (!tenantOk || !memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
            processHitChain(processor, user, tenantId, hits, index + 1, nextPageSort, results, listener);
            return;
        }

        Long baseline = extractOrphanBaseline(hit.getSourceAsMap());
        processor.dryRunContainer(container.getConfiguration(), containerId, baseline, ActionListener.wrap(result -> {
            results.add(result);
            processHitChain(processor, user, tenantId, hits, index + 1, nextPageSort, results, listener);
        }, e -> {
            log.warn("Skipping container {} in retention dry-run: evaluation failed", containerId, e);
            processHitChain(processor, user, tenantId, hits, index + 1, nextPageSort, results, listener);
        }));
    }

    private MLMemoryContainer parseContainer(String sourceJson) throws java.io.IOException {
        try (
            XContentParser parser = jsonXContent
                .createParser(xContentRegistry, org.opensearch.common.xcontent.LoggingDeprecationHandler.INSTANCE, sourceJson)
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return MLMemoryContainer.parse(parser);
        }
    }

    /**
     * Reads the stored orphan-sweep baseline timestamp from a container source map so the dry-run can honor the
     * same first-observation grace window the real orphan sweep applies. Returns {@code null} when not stamped.
     */
    private Long extractOrphanBaseline(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Object value = source.get(ORPHAN_SWEEP_BASELINE_TIME_FIELD);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
