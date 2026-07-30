/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionAction;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse.TriggerStatus;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Triggers the memory retention job on demand. Delegates to the same
 * {@link MemoryRetentionJobProcessor} singleton the scheduler uses and invokes its
 * {@link MemoryRetentionJobProcessor#triggerRun()} guard-and-kickoff path, so behavior is identical
 * to a scheduled run. The pipeline is fully async; this action acknowledges the trigger promptly and
 * does not block until deletions complete.
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportExecuteMemoryRetentionAction extends HandledTransportAction<ActionRequest, MLExecuteMemoryRetentionResponse> {

    final Client client;
    final ClusterService clusterService;
    final ThreadPool threadPool;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportExecuteMemoryRetentionAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLExecuteMemoryRetentionAction.NAME, transportService, actionFilters, MLExecuteMemoryRetentionRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLExecuteMemoryRetentionResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        try {
            // Reuse the scheduler's per-node singleton so the on-demand trigger and the scheduled run
            // share the same in-progress guard ON THIS NODE. This dedupes the common cases (repeated
            // triggers, or a trigger racing the scheduler, when both land on the same node). NOTE: the
            // guard is a per-JVM AtomicBoolean and this action runs locally on the coordinating node
            // WITHOUT the scheduler's cluster-wide LockService lock, so it does not prevent a trigger on
            // one node from running concurrently with the scheduled run (or another trigger) on a
            // different node. The pipeline tolerates this (deletes are idempotent, version conflicts are
            // handled, orphan sweep is baseline-gated); cluster-wide mutual exclusion would require
            // routing this path through LockService and is intentionally out of scope here.
            MemoryRetentionJobProcessor processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
            TriggerStatus status = processor.triggerRun();
            actionListener.onResponse(new MLExecuteMemoryRetentionResponse(status, messageFor(status)));
        } catch (Exception e) {
            log.error("Failed to trigger memory retention job on demand", e);
            actionListener
                .onFailure(new OpenSearchStatusException("Failed to trigger memory retention job", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private static String messageFor(TriggerStatus status) {
        switch (status) {
            case TRIGGERED:
                return "Memory retention job triggered. Deletions run asynchronously per configured retention policy.";
            case ALREADY_RUNNING:
                return "Memory retention job is already in progress; this invocation was skipped to avoid double-running.";
            case RETENTION_DISABLED:
                return "Memory retention is disabled (plugins.ml_commons.memory.retention_enabled=false); nothing was triggered.";
            case MULTI_TENANCY_ENABLED:
                return "Memory retention job cannot run while multi-tenancy is enabled; the native client lacks tenant routing.";
            default:
                return null;
        }
    }
}
