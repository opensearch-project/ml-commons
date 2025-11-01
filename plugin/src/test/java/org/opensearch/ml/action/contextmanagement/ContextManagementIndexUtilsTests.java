/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.transport.client.Client;

public class ContextManagementIndexUtilsTests {

    private ContextManagementIndexUtils contextManagementIndexUtils;
    private Client client;
    private ClusterService clusterService;

    @Before
    public void setUp() {
        client = mock(Client.class);
        clusterService = mock(ClusterService.class);
        contextManagementIndexUtils = new ContextManagementIndexUtils(client, clusterService);
    }

    @Test
    public void testGetIndexName() {
        // Act
        String indexName = ContextManagementIndexUtils.getIndexName();

        // Assert
        assertEquals("ml_context_management_templates", indexName);
    }

    @Test
    public void testDoesIndexExist_True() {
        // Arrange
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(true);

        // Act
        boolean exists = contextManagementIndexUtils.doesIndexExist();

        // Assert
        assertTrue(exists);
    }

    @Test
    public void testDoesIndexExist_False() {
        // Arrange
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ml_context_management_templates")).thenReturn(false);

        // Act
        boolean exists = contextManagementIndexUtils.doesIndexExist();

        // Assert
        assertFalse(exists);
    }
}
