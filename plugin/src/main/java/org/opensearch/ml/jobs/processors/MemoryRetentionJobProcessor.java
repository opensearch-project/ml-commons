/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

public class MemoryRetentionJobProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MemoryRetentionJobProcessor.class);

    private static MemoryRetentionJobProcessor instance;

    public static MemoryRetentionJobProcessor getInstance(ClusterService clusterService, Client client, ThreadPool threadPool) {
        if (instance != null) {
            return instance;
        }

        synchronized (MemoryRetentionJobProcessor.class) {
            if (instance != null) {
                return instance;
            }

            instance = new MemoryRetentionJobProcessor(clusterService, client, threadPool);
            return instance;
        }
    }

    @VisibleForTesting
    public static synchronized void reset() {
        instance = null;
    }

    public MemoryRetentionJobProcessor(ClusterService clusterService, Client client, ThreadPool threadPool) {
        super(clusterService, client, threadPool);
    }

    @Override
    public void run() {
        if (ML_COMMONS_MULTI_TENANCY_ENABLED.get(clusterService.getSettings())) {
            log.warn("Memory retention job skipped: multi-tenancy is enabled and native client lacks tenant routing");
            return;
        }

        log.info("Memory retention job triggered");
    }
}
