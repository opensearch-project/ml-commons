/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.spy;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.test.OpenSearchIntegTestCase.Scope;

@OpenSearchIntegTestCase.ClusterScope(scope = Scope.SUITE, numDataNodes = 3)
public class MLModelAutoReLoaderITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Mock
    private DiscoveryNodeHelper nodeHelper;
    private Settings settings;
    private MLModelAutoReLoader mlModelAutoReLoader;
    @Mock
    private MLStats mlStats;
    @Mock
    private ModelHelper modelHelper;

    @Before
    public void setup() throws Exception {
        settings = Settings.builder().put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true).build();
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        mlStats = spy(new MLStats(stats));

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService(), settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService(), client(), client().threadPool(), xContentRegistry(), nodeHelper, settings, mlStats)
        );
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testIsExistedIndex() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        createIndex(ML_MODEL_RELOAD_INDEX);

        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    public void testSaveLatestReTryTimes_getReTryTimes() {
        assertFalse(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        String localNodeId = clusterService().localNode().getId();

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 0);
        assertTrue(mlModelAutoReLoader.isExistedIndex(ML_MODEL_RELOAD_INDEX));
        Integer retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        mlModelAutoReLoader.saveLatestReTryTimes(localNodeId, 1);
        retryTimes = mlModelAutoReLoader.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));
    }

//    @Ignore
//    public void testAutoReLoadModelByNodeAndModelId() {
//        String localNodeId = clusterService().localNode().getId();
//        String modelId = "modelId1";
//
//        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
//    }

    public void testAutoReLoadModelByNodeId() {
        String localNodeId = clusterService().localNode().getId();

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModel() {
        mlModelAutoReLoader.autoReLoadModel();
    }
}
