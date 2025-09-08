/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import java.util.List;

import org.opensearch.core.action.ActionListener;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

/**
 * Interface representing an index insight execution task
 */
public interface IndexInsightTask {

    /**
     * Execute the index insight task
     */
    void execute(String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Get the task type
     */
    MLIndexInsightType getTaskType();

    /**
     * Get the source index
     */
    String getSourceIndex();

    /**
     * Get the prerequisites of this task
     */
    List<MLIndexInsightType> getPrerequisites();

    /**
     * Get the client instance
     */
    Client getClient();

    /**
     * Get the SDK client instance
     */
    SdkClient getSdkClient();

    /**
     * Run the specific task logic
     */
    void runTask(String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Create prerequisite task instance
     */
    IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType);

}
