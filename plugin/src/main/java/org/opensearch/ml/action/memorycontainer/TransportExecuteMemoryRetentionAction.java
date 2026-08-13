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
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionAction;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse.TriggerStatus;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.ml.utils.RestActionUtils;
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
 * {@link MemoryRetentionJobProcessor#triggerRun(org.opensearch.core.action.ActionListener)} guard-and-kickoff path, so behavior is identical
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

        // Admin-only: this triggers a CLUSTER-WIDE retention run that evaluates every container's policy and
        // deletes expired memories across the cluster. Unlike per-container operations, there is no single
        // container whose access we can check, so we restrict it to admins (all_access). This prevents a
        // lower-privilege caller from forcing early deletion of data they could not otherwise reach, while
        // still allowing the scheduled run (which uses the system context, no user) to proceed normally.
        User user = RestActionUtils.getUserContext(client);
        if (user != null && (user.getRoles() == null || !user.getRoles().contains("all_access"))) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "Only administrators (all_access) may trigger the memory retention job on demand.",
                        RestStatus.FORBIDDEN
                    )
                );
            return;
        }

        try {
            // Reuse the scheduler's per-node singleton so the on-demand trigger and the scheduled run share
            // the same in-progress guard ON THIS NODE and, crucially, the same cluster-wide JobScheduler
            // LockService lock. triggerRun() acquires that lock (same lock index + doc id the scheduled run
            // uses) and HOLDS it for the whole async delete pipeline, so an on-demand trigger on one node
            // will report ALREADY_RUNNING rather than overlap a scheduled (or on-demand) run on another node.
            // The call is async because lock acquisition is async; it reports the outcome via the listener.
            MemoryRetentionJobProcessor processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
            processor
                .triggerRun(
                    ActionListener
                        .wrap(status -> actionListener.onResponse(new MLExecuteMemoryRetentionResponse(status, messageFor(status))), e -> {
                            log.error("Failed to trigger memory retention job on demand", e);
                            actionListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "Failed to trigger memory retention job",
                                        RestStatus.INTERNAL_SERVER_ERROR
                                    )
                                );
                        })
                );
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
            case REMOTE_METADATA_STORE:
                return "Memory retention job cannot run while a remote metadata store is configured; "
                    + "the container registry is not in the local cluster.";
            case MULTI_TENANCY_ENABLED:
                return "Memory retention job cannot run while multi-tenancy is enabled; the native client lacks tenant routing.";
            default:
                return null;
        }
    }
}
