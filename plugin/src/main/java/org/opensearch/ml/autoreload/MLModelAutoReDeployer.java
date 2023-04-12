/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.autoreload;

import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.threadpool.ThreadPool;

import java.util.List;

import static org.opensearch.ml.settings.MLCommonsSettings.*;

public class MLModelAutoReDeployer {

    private final ClusterService clusterService;
    private final Client client;
    private final ThreadPool threadPool;
    private final NodeEnvironment nodeEnvironment;
    private final Settings settings;
    private final NamedXContentRegistry xContentRegistry;
    private volatile Boolean enableAutoReDeployModel;
    private volatile Boolean onlyRunOnMlNode;
    private volatile Integer autoDeployMaxRetryTimes;
    private volatile boolean allowCustomDeploymentPlan;
    private volatile boolean isMasterNode;
    public MLModelAutoReDeployer(
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        Settings settings,
        NodeEnvironment nodeEnvironment,
        NamedXContentRegistry xContentRegistry
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.threadPool = threadPool;
        this.settings = settings;
        this.nodeEnvironment = nodeEnvironment;
        this.xContentRegistry = xContentRegistry;

        enableAutoReDeployModel = ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.get(settings);
        onlyRunOnMlNode = ML_COMMONS_ONLY_RUN_ON_ML_NODE.get(settings);
        autoDeployMaxRetryTimes = ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.get(settings);
        allowCustomDeploymentPlan = ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE, it -> enableAutoReDeployModel = it);

        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_ONLY_RUN_ON_ML_NODE, it -> onlyRunOnMlNode = it);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES, it -> autoDeployMaxRetryTimes = it);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN, it -> allowCustomDeploymentPlan = it);
    }


    public void autoReDeployModel(List<DiscoveryNode> addedNodes) {

    }
}
