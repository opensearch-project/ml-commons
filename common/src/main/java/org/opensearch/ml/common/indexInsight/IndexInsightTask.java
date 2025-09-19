/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import java.util.List;

import org.opensearch.core.action.ActionListener;

/**
 * Interface representing an index insight execution task
 */
public interface IndexInsightTask {

    /**
     * Execute the index insight task
     */
    void execute(String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Get the prerequisites of this task
     */
    List<MLIndexInsightType> getPrerequisites();

    /**
     * Run the specific task logic
     */
    void runTask(String tenantId, ActionListener<IndexInsight> listener);

    /**
     * Create prerequisite task instance
     */
    IndexInsightTask createPrerequisiteTask(MLIndexInsightType prerequisiteType);

}
