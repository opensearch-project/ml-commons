/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.transport.client.Client;

public class ContextManagementTemplateServiceTests {

    private ContextManagementTemplateService contextManagementTemplateService;
    private MLIndicesHandler mlIndicesHandler;
    private Client client;
    private ClusterService clusterService;

    @Before
    public void setUp() {
        mlIndicesHandler = mock(MLIndicesHandler.class);
        client = mock(Client.class);
        clusterService = mock(ClusterService.class);
        contextManagementTemplateService = new ContextManagementTemplateService(mlIndicesHandler, client, clusterService);
    }

    @Test
    public void testConstructor() {
        // Assert
        assertNotNull(contextManagementTemplateService);
    }
}
