/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
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
}
