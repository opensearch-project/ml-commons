/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.test.OpenSearchTestCase;

public class StatNamesTests extends OpenSearchTestCase {

    public void testRequestCountStat() {
        String statName = StatNames.requestCountStat(FunctionName.KMEANS, ActionName.TRAIN);
        assertEquals("ml_kmeans_train_request_count", statName);
    }

    public void testFailureCountStat() {
        String statName = StatNames.failureCountStat(FunctionName.KMEANS, ActionName.TRAIN);
        assertEquals("ml_kmeans_train_failure_count", statName);
    }

    public void testExecutingRequestCountStat() {
        String statName = StatNames.executingRequestCountStat(FunctionName.KMEANS, ActionName.TRAIN);
        assertEquals("ml_kmeans_train_executing_request_count", statName);
    }

    public void testModelCountStat() {
        String statName = StatNames.modelCountStat(FunctionName.KMEANS);
        assertEquals("ml_kmeans_model_count", statName);
    }
}
