/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.stats;

import static org.opensearch.ml.utils.IntegTestUtils.TESTING_DATA;
import static org.opensearch.ml.utils.IntegTestUtils.generateMLTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.verifyGeneratedTestingData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.opensearch.action.ActionFuture;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.stats.StatNames;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(transportClientRatio = 0.9)
public class MLStatsNodeIT extends OpenSearchIntegTestCase {
    @Before
    public void initTestingData() throws ExecutionException, InterruptedException {
        generateMLTestingData();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    public void testGeneratedTestingData() throws ExecutionException, InterruptedException {
        verifyGeneratedTestingData(TESTING_DATA);
    }

    public void testNormalCase() throws ExecutionException, InterruptedException {
        MLStatsNodesRequest request = new MLStatsNodesRequest(new String[0]);
        request.addStat(StatNames.ML_EXECUTING_TASK_COUNT.getName());

        ActionFuture<MLStatsNodesResponse> future = client().execute(MLStatsNodesAction.INSTANCE, request);
        MLStatsNodesResponse response = future.get();
        assertNotNull(response);

        List<MLStatsNodeResponse> responseList = response.getNodes();
        assertNotNull(responseList);
        assertEquals(1, responseList.size());

        MLStatsNodeResponse nodeResponse = responseList.get(0);
        Map<String, Object> statsMap = nodeResponse.getStatsMap();

        assertEquals(1, statsMap.size());
        assertEquals(0l, statsMap.get("ml_executing_task_count"));
    }
}
