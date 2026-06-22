/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MemoryRetentionJobProcessorTests {

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    private MemoryRetentionJobProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        MemoryRetentionJobProcessor.reset();
        processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
    }

    @Test
    public void testGetInstance() {
        MemoryRetentionJobProcessor instance1 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        MemoryRetentionJobProcessor instance2 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        assertSame(instance1, instance2);
    }

    @Test
    public void testRunSkipsWhenMultiTenancyEnabled() {
        Settings settings = Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build();
        when(clusterService.getSettings()).thenReturn(settings);

        processor.run();

        // No exception thrown, method returns early with warning log
    }

    @Test
    public void testRunExecutesWhenMultiTenancyDisabled() {
        Settings settings = Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", false).build();
        when(clusterService.getSettings()).thenReturn(settings);

        processor.run();

        // No exception thrown, logs "Memory retention job triggered"
    }

    @Test
    public void testRunExecutesWithDefaultSettings() {
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);

        processor.run();

        // Default multi_tenancy_enabled is false, so job should execute
    }
}
