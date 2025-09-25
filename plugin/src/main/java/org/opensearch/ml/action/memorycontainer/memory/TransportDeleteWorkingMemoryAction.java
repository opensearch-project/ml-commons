/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteWorkingMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteWorkingMemoryRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportDeleteWorkingMemoryAction extends HandledTransportAction<MLDeleteWorkingMemoryRequest, DeleteResponse> {
    private static final Logger log = LogManager.getLogger(TransportDeleteWorkingMemoryAction.class);

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportDeleteWorkingMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        Settings settings,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLDeleteWorkingMemoryAction.NAME, transportService, actionFilters, MLDeleteWorkingMemoryRequest::new);
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLDeleteWorkingMemoryRequest request, ActionListener<DeleteResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        String memoryContainerId = request.getMemoryContainerId();
        String workingMemoryId = request.getWorkingMemoryId();

        // First, get the memory container to determine the index name
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            MemoryConfiguration configuration = container.getConfiguration();
            String workingMemoryIndex = configuration.getWorkingMemoryIndexName();

            // Delete the working memory document
            DeleteRequest deleteRequest = new DeleteRequest(workingMemoryIndex, workingMemoryId);
            client.delete(deleteRequest, ActionListener.wrap(deleteResponse -> { actionListener.onResponse(deleteResponse); }, error -> {
                log.error("Failed to delete working memory", error);
                actionListener.onFailure(error);
            }));
        }, error -> {
            log.error("Failed to get memory container", error);
            actionListener.onFailure(error);
        }));
    }
}
