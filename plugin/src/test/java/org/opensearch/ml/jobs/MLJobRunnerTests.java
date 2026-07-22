/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLJobRunnerTests {

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private JobExecutionContext jobExecutionContext;

    @Mock
    private MLJobParameter jobParameter;

    private MLJobRunner jobRunner;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        jobRunner = MLJobRunner.getInstance();
        jobRunner.initialize(clusterService, threadPool, client, sdkClient, connectorAccessControlHelper, mlFeatureEnabledSetting);
    }

    @Test
    public void testGetInstance() {
        MLJobRunner instance1 = MLJobRunner.getInstance();
        MLJobRunner instance2 = MLJobRunner.getInstance();
        assertSame(instance1, instance2);
    }

    @Test(expected = IllegalStateException.class)
    public void testRunJobWithoutInitialization() {
        MLJobRunner uninitializedRunner = new MLJobRunner();
        uninitializedRunner.runJob(jobParameter, jobExecutionContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRunJobWithNullJobType() {
        when(jobParameter.getJobType()).thenReturn(null);
        jobRunner.runJob(jobParameter, jobExecutionContext);
    }

    @Test(expected = IllegalStateException.class)
    public void testRunJobWithDisabledJob() {
        when(jobParameter.isEnabled()).thenReturn(false);
        when(jobParameter.getJobType()).thenReturn(MLJobType.STATS_COLLECTOR);
        jobRunner.runJob(jobParameter, jobExecutionContext);
    }

    @Test
    public void testRunJobWithMemoryRetentionType() {
        MemoryRetentionJobProcessor.reset();
        when(jobParameter.isEnabled()).thenReturn(true);
        when(jobParameter.getJobType()).thenReturn(MLJobType.MEMORY_RETENTION);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);

        LockService lockService = mock(LockService.class);
        when(jobExecutionContext.getLockService()).thenReturn(lockService);

        ExecutorService executorService = mock(ExecutorService.class);
        when(threadPool.generic()).thenReturn(executorService);

        jobRunner.runJob(jobParameter, jobExecutionContext);
    }
}
