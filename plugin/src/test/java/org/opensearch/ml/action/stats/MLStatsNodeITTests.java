/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import static org.opensearch.ml.stats.MLNodeLevelStat.ML_EXECUTING_TASK_COUNT;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_DATA;
import static org.opensearch.ml.utils.IntegTestUtils.generateMLTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.verifyGeneratedTestingData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

public class MLStatsNodeITTests extends OpenSearchIntegTestCase {
    @Before
    public void initTestingData() throws ExecutionException, InterruptedException {
        generateMLTestingData();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    public void testGeneratedTestingData() throws ExecutionException, InterruptedException {
        verifyGeneratedTestingData(TESTING_DATA);
    }

    public void testNormalCase() throws ExecutionException, InterruptedException {
        MLStatsNodesRequest request = new MLStatsNodesRequest(new String[0], new MLStatsInput());
        request.addNodeLevelStats(Set.of(ML_EXECUTING_TASK_COUNT));

        ActionFuture<MLStatsNodesResponse> future = client().execute(MLStatsNodesAction.INSTANCE, request);
        MLStatsNodesResponse response = future.get();
        assertNotNull(response);

        List<MLStatsNodeResponse> responseList = response.getNodes();
        // TODO: the responseList size here is not a fixed value. Comment out this assertion until this flaky test is fixed
        // assertEquals(1, responseList.size());
        assertNotNull(responseList);

        MLStatsNodeResponse nodeResponse = responseList.get(0);
        assertEquals(1, nodeResponse.getNodeLevelStatSize());
        assertEquals(0l, nodeResponse.getNodeLevelStat(ML_EXECUTING_TASK_COUNT));
    }
}
